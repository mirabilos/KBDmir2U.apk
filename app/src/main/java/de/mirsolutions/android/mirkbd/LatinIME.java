/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.mirsolutions.android.mirkbd;

import de.mirsolutions.android.mirkbd.LatinIMEUtil.RingCharBuffer;

import org.xmlpull.v1.XmlPullParserException;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.speech.SpeechRecognizer;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.HapticFeedbackConstants;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements
        ComposeSequencing,
        LatinKeyboardBaseView.OnKeyboardActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "KBDmir2U/IME";
    private static final boolean PERF_DEBUG = false;
    static final boolean DEBUG = false;
    static final boolean TRACE = false;
    static Map<Integer, String> ESC_SEQUENCES;
    static Map<Integer, Integer> CTRL_SEQUENCES;

    private static final String PREF_SOUND_ON = "sound_on";
    private static final String PREF_POPUP_ON = "popup_on";

    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_INPUT_LANGUAGE = "input_language";
    static final String PREF_FULLSCREEN_OVERRIDE = "fullscreen_override";
    static final String PREF_FORCE_KEYBOARD_ON = "force_keyboard_on";
    static final String PREF_KEYBOARD_NOTIFICATION = "keyboard_notification";
    static final String PREF_CONNECTBOT_TAB_HACK = "connectbot_tab_hack";
    static final String PREF_FULL_KEYBOARD_IN_PORTRAIT = "full_keyboard_in_portrait";
    static final String PREF_HEIGHT_PORTRAIT = "settings_height_portrait";
    static final String PREF_HEIGHT_LANDSCAPE = "settings_height_landscape";
    static final String PREF_HINT_MODE = "pref_hint_mode";
    static final String PREF_LONGPRESS_TIMEOUT = "pref_long_press_duration";
    static final String PREF_RENDER_MODE = "pref_render_mode";
    static final String PREF_SWIPE_UP = "pref_swipe_up";
    static final String PREF_SWIPE_DOWN = "pref_swipe_down";
    static final String PREF_SWIPE_LEFT = "pref_swipe_left";
    static final String PREF_SWIPE_RIGHT = "pref_swipe_right";
    static final String PREF_VOL_UP = "pref_vol_up";
    static final String PREF_VOL_DOWN = "pref_vol_down";

    private static final int MSG_UPDATE_SHIFT_STATE = 2;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

    static final int ASCII_ENTER = '\n';
    static final int ASCII_SPACE = ' ';
    static final int ASCII_PERIOD = '.';

    // Contextual menu positions
    private static final int POS_METHOD = 0;
    private static final int POS_SETTINGS = 1;

    private AlertDialog mOptionsDialog;

    /* package */KeyboardSwitcher mKeyboardSwitcher;

    private Resources mResources;

    private String mInputLocale;
    private String mSystemLocale;
    private LanguageSwitcher mLanguageSwitcher;

    private StringBuilder mComposing = new StringBuilder();
    private int mCommittedLength;
    private CharSequence mBestWord;
    private boolean mAutoSpace;
    private boolean mJustAddedAutoSpace;
    // TODO move this state variable outside LatinIME
    private boolean mModCtrl;
    private boolean mModAlt;
    private boolean mModMeta;
    private boolean mModFn;
    // Saved shift state when leaving alphabet mode, or when applying multitouch shift
    private int mSavedShiftState;
    private boolean mPasswordText;
    private boolean mSoundOn;
    private boolean mPopupOn;
    private boolean mDeadKeysActive;
    private boolean mConnectbotTabHack;
    private boolean mFullscreenOverride;
    private boolean mForceKeyboardOn;
    private boolean mKeyboardNotification;
    private String mSwipeUpAction;
    private String mSwipeDownAction;
    private String mSwipeLeftAction;
    private String mSwipeRightAction;
    private String mVolUpAction;
    private String mVolDownAction;

    public static final GlobalKeyboardSettings sKeyboardSettings = new GlobalKeyboardSettings(); 
    static LatinIME sInstance;
    
    private int mHeightPortrait;
    private int mHeightLandscape;
    private int mOrientation;
    // Keep track of the last selection range to decide if we need to show word
    // alternatives
    private int mLastSelectionStart;
    private int mLastSelectionEnd;

    private boolean mJustAccepted;
    private CharSequence mJustRevertedSeparator;
    private int mDeleteCount;
    private long mLastKeyTime;

    // Modifier keys state
    private ModifierKeyState mShiftKeyState = new ModifierKeyState();
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState();
    private ModifierKeyState mCtrlKeyState = new ModifierKeyState();
    private ModifierKeyState mAltKeyState = new ModifierKeyState();
    private ModifierKeyState mMetaKeyState = new ModifierKeyState();
    private ModifierKeyState mFnKeyState = new ModifierKeyState();

    // Compose sequence handling
    private boolean mComposeMode = false;
    private ComposeSequence mComposeBuffer = new ComposeSequence(this);
    private ComposeSequence mDeadAccentBuffer = new DeadAccentSequence(this);

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private final float FX_VOLUME = -1.0f;
    private final float FX_VOLUME_RANGE_DB = 72.0f;
    private boolean mSilentMode;

    /* package */String mWordSeparators;
    private String mSentenceSeparators;
    private boolean mConfigurationChanging;

    // Keeps track of most recently inserted text (multi-character key) for
    // reverting
    private CharSequence mEnteredText;
    private boolean mRefreshKeyboardRequired;

    private NotificationReceiver mNotificationReceiver;

    /* package */Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_SHIFT_STATE:
                updateShiftKeyState(getCurrentInputEditorInfo());
                break;
            }
        }
    };

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate(), os.version=" + System.getProperty("os.version"));
        LatinImeLogger.init(this);
        KeyboardSwitcher.init(this);
        super.onCreate();
        sInstance = this;
        // setStatusIcon(R.drawable.ime_qwerty);
        mResources = getResources();
        final Configuration conf = mResources.getConfiguration();
        mOrientation = conf.orientation;
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        mLanguageSwitcher = new LanguageSwitcher(this);
        mLanguageSwitcher.loadLocales(prefs);
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mKeyboardSwitcher.setLanguageSwitcher(mLanguageSwitcher);
        mSystemLocale = conf.locale.toString();
        mLanguageSwitcher.setSystemLocale(conf.locale);
        String inputLanguage = mLanguageSwitcher.getInputLanguage();
        if (inputLanguage == null) {
            inputLanguage = conf.locale.toString();
        }
        Resources res = getResources();
        mConnectbotTabHack = prefs.getBoolean(PREF_CONNECTBOT_TAB_HACK,
                res.getBoolean(R.bool.default_connectbot_tab_hack));
        mFullscreenOverride = prefs.getBoolean(PREF_FULLSCREEN_OVERRIDE,
                res.getBoolean(R.bool.default_fullscreen_override));
        mForceKeyboardOn = prefs.getBoolean(PREF_FORCE_KEYBOARD_ON,
                res.getBoolean(R.bool.default_force_keyboard_on));
        mKeyboardNotification = prefs.getBoolean(PREF_KEYBOARD_NOTIFICATION,
                res.getBoolean(R.bool.default_keyboard_notification));
        mHeightPortrait = getHeight(prefs, PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait));
        mHeightLandscape = getHeight(prefs, PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape));
        LatinIME.sKeyboardSettings.hintMode = Integer.parseInt(prefs.getString(PREF_HINT_MODE, res.getString(R.string.default_hint_mode)));
        LatinIME.sKeyboardSettings.longpressTimeout = getPrefInt(prefs, PREF_LONGPRESS_TIMEOUT, res.getString(R.string.default_long_press_duration));
        LatinIME.sKeyboardSettings.renderMode = getPrefInt(prefs, PREF_RENDER_MODE, res.getString(R.string.default_render_mode));
        mSwipeUpAction = prefs.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up));
        mSwipeDownAction = prefs.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down));
        mSwipeLeftAction = prefs.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left));
        mSwipeRightAction = prefs.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right));
        mVolUpAction = prefs.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up));
        mVolDownAction = prefs.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down));
        sKeyboardSettings.initPrefs(prefs, res);

        updateKeyboardOptions();

        LatinIMEUtil.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                initSuggest(inputLanguage);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(
                        inputLanguage, e);
            }
        }

        mOrientation = conf.orientation;

        // register to receive ringer mode changes for silent mode
        IntentFilter filter = new IntentFilter(
                AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
        prefs.registerOnSharedPreferenceChangeListener(this);
        setNotification(mKeyboardNotification);
    }

    private void updateKeyboardOptions() {
        //Log.i(TAG, "setFullKeyboardOptions " + fullInPortrait + " " + heightPercentPortrait + " " + heightPercentLandscape);
        // Convert overall keyboard height to per-row percentage
        int screenHeightPercent = isPortrait() ? mHeightPortrait : mHeightLandscape;
        LatinIME.sKeyboardSettings.keyboardHeightPercent = (float) screenHeightPercent;
    }
    
    private void setNotification(boolean visible) {
    	final String ACTION = "de.mirsolutions.android.mirkbd.SHOW";
        final int ID = 1;
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        
        if (visible && mNotificationReceiver == null) {
            int icon = R.drawable.icon;
            CharSequence text = "Keyboard notification enabled.";
            long when = System.currentTimeMillis();

            // TODO: clean this up?
            mNotificationReceiver = new NotificationReceiver(this);
            final IntentFilter pFilter = new IntentFilter(ACTION);
            registerReceiver(mNotificationReceiver, pFilter);
            
            Intent notificationIntent = new Intent(ACTION);
            
            PendingIntent contentIntent = PendingIntent.getBroadcast(getApplicationContext(), 1, notificationIntent, 0);
            //PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            
            String title = "Show Hacker's Keyboard";
            String body = "Select this to open the keyboard. Disable in settings.";

            Notification notification = new Notification.Builder(getApplicationContext())
                    .setAutoCancel(false) //Make this notification automatically dismissed when the user touches it -> false.
                    .setTicker(text)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setWhen(when)
                    .setSmallIcon(icon)
                    .setContentIntent(contentIntent)
                    .getNotification();
            
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
            mNotificationManager.notify(ID, notification);
        } else if (mNotificationReceiver != null) {
            mNotificationManager.cancel(ID);
            unregisterReceiver(mNotificationReceiver);
            mNotificationReceiver = null;
        }
    }
    
    private boolean isPortrait() {
        return (mOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    private void initSuggest(String locale) {
        mInputLocale = locale;

        Resources orig = getResources();
        Configuration conf = orig.getConfiguration();
        orig.updateConfiguration(conf, orig.getDisplayMetrics());

        mWordSeparators = mResources.getString(R.string.word_separators);
        mSentenceSeparators = mResources
                .getString(R.string.sentence_separators);

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        if (mNotificationReceiver != null) {
        	unregisterReceiver(mNotificationReceiver);
            mNotificationReceiver = null;
        }
        LatinImeLogger.commit();
        LatinImeLogger.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        Log.i(TAG, "onConfigurationChanged()");
        // If the system locale changes and is different from the saved
        // locale (mSystemLocale), then reload the input locale list from the
        // latin ime settings (shared prefs) and reset the input locale
        // to the first one.
        final String systemLocale = conf.locale.toString();
        if (!TextUtils.equals(systemLocale, mSystemLocale)) {
            mSystemLocale = systemLocale;
            if (mLanguageSwitcher != null) {
                mLanguageSwitcher.loadLocales(PreferenceManager
                        .getDefaultSharedPreferences(this));
                mLanguageSwitcher.setSystemLocale(conf.locale);
                toggleLanguage(true, true);
            } else {
                reloadKeyboards();
            }
        }
        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null)
                ic.finishComposingText(); // For voice input
            mOrientation = conf.orientation;
            reloadKeyboards();
        }
        mConfigurationChanging = true;
        super.onConfigurationChanged(conf);
        mConfigurationChanging = false;
    }

    @Override
    public View onCreateInputView() {
        mKeyboardSwitcher.recreateInputView();
        mKeyboardSwitcher.makeKeyboards(true);
        mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT, 0);
        return mKeyboardSwitcher.getInputView();
    }

    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
    	return new MyInputMethodImpl();
    }
    
    IBinder mToken;
    public class MyInputMethodImpl extends InputMethodImpl {
    	@Override
    	public void attachToken(IBinder token) {
    		super.attachToken(token);
    		Log.i(TAG, "attachToken " + token);
    		if (mToken == null) {
    			mToken = token;
    		}
    	}
    }
    
    private void resetPrediction() {
        mComposing.setLength(0);
        mDeleteCount = 0;
        mJustAddedAutoSpace = false;
    }
    
    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        sKeyboardSettings.editorPackageName = attribute.packageName;
        sKeyboardSettings.editorFieldName = attribute.fieldName;
        sKeyboardSettings.editorFieldId = attribute.fieldId;
        sKeyboardSettings.editorInputType = attribute.inputType;

        //Log.i(TAG, "onStartInputView " + attribute + ", inputType= " + Integer.toHexString(attribute.inputType) + ", restarting=" + restarting);
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        // In landscape mode, this method gets called without the input view
        // being created.
        if (inputView == null) {
            return;
        }

        if (mRefreshKeyboardRequired) {
            mRefreshKeyboardRequired = false;
            toggleLanguage(true, true);
        }

        mKeyboardSwitcher.makeKeyboards(false);

        TextEntryState.newSession(this);

        // Most such things we decide below in the switch statement, but we need to know
        // now whether this is a password text field, because we need to know now (before
        // the switch statement) whether we want to enable the voice button.
        mPasswordText = false;
        int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == 0xe0 /* EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD */
        ) {
            if ((attribute.inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
                mPasswordText = true;
            }
        }

        mModCtrl = false;
        mModAlt = false;
        mModMeta = false;
        mModFn = false;
        mEnteredText = null;

        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
        case EditorInfo.TYPE_CLASS_NUMBER:
        case EditorInfo.TYPE_CLASS_DATETIME:
            // fall through
            // NOTE: For now, we use the phone keyboard for NUMBER and DATETIME
            // until we get
            // a dedicated number entry keypad.
            // TODO: Use a dedicated number entry keypad here when we get one.
        case EditorInfo.TYPE_CLASS_PHONE:
            mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_PHONE,
                    attribute.imeOptions);
            break;
        case EditorInfo.TYPE_CLASS_TEXT:
            mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions);
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME
                    || !mLanguageSwitcher.allowAutoSpace()) {
                mAutoSpace = false;
            } else {
                mAutoSpace = true;
            }
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_EMAIL,
                        attribute.imeOptions);
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_URL,
                        attribute.imeOptions);
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_IM,
                        attribute.imeOptions);
            } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_WEB,
                        attribute.imeOptions);
            }
            break;
        default:
            mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                    attribute.imeOptions);
        }
        inputView.closing();
        resetPrediction();
        loadSettings();
        updateShiftKeyState(attribute);

        inputView.setPreviewEnabled(mPopupOn);
        inputView.setProximityCorrectionEnabled(true);
        if (TRACE)
            Debug.startMethodTracing("/data/trace/latinime");
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();

        LatinImeLogger.commit();
        onAutoCompletionStateChanged(false);

        if (mKeyboardSwitcher.getInputView() != null) {
            mKeyboardSwitcher.getInputView().closing();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onUpdateExtractedText(int token, ExtractedText text) {
        super.onUpdateExtractedText(token, text);
        InputConnection ic = getCurrentInputConnection();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd, int candidatesStart,
            int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        if (DEBUG) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose="
                    + oldSelEnd + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + candidatesStart + ", ce=" + candidatesEnd);
        }

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (!mJustAccepted) {
            switch (TextEntryState.getState()) {
            case ACCEPTED_DEFAULT:
                TextEntryState.reset();
                // fall through
            case SPACE_AFTER_PICKED:
                mJustAddedAutoSpace = false; // The user moved the cursor.
                break;
            }
        }
        mJustAccepted = false;
        postUpdateShiftKeyState();

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;
    }

    @Override
    public void hideWindow() {
        LatinImeLogger.commit();
        onAutoCompletionStateChanged(false);

        if (TRACE)
            Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        super.hideWindow();
        TextEntryState.endSession();
    }

    @Override
    public boolean onEvaluateInputViewShown() {
    	boolean parent = super.onEvaluateInputViewShown();
    	boolean wanted = mForceKeyboardOn || parent;
    	//Log.i(TAG, "OnEvaluateInputViewShown, parent=" + parent + " + " wanted=" + wanted);
    	return wanted;
    }
    
    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (!isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float displayHeight = dm.heightPixels;
        // If the display is more than X inches high, don't go to fullscreen
        // mode
        float dimen = getResources().getDimension(
                R.dimen.max_height_for_fullscreen);
        if (displayHeight > dimen || mFullscreenOverride || isConnectbot()) {
            return false;
        } else {
            return super.onEvaluateFullscreenMode();
        }
    }

    public boolean isKeyboardVisible() {
        return (mKeyboardSwitcher != null
                && mKeyboardSwitcher.getInputView() != null
                && mKeyboardSwitcher.getInputView().isShown());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            if (event.getRepeatCount() == 0
                    && mKeyboardSwitcher.getInputView() != null) {
                if (mKeyboardSwitcher.getInputView().handleBack()) {
                    return true;
                }
            }
            break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            break;
        case KeyEvent.KEYCODE_VOLUME_UP:
            if (!mVolUpAction.equals("none") && isKeyboardVisible()) {
                return true;
            }
            break;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            if (!mVolDownAction.equals("none") && isKeyboardVisible()) {
                return true;
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
            // Enable shift key and DPAD to do selections
            if (inputView != null && inputView.isShown()
                    && inputView.getShiftState() == Keyboard.SHIFT_ON) {
                event = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), event.getKeyCode(), event
                                .getRepeatCount(), event.getDeviceId(), event
                                .getScanCode(), KeyEvent.META_SHIFT_LEFT_ON
                                | KeyEvent.META_SHIFT_ON);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null)
                    ic.sendKeyEvent(event);
                return true;
            }
            break;
        case KeyEvent.KEYCODE_VOLUME_UP:
            if (!mVolUpAction.equals("none") && isKeyboardVisible()) {
                return doSwipeAction(mVolUpAction);
            }
            break;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            if (!mVolDownAction.equals("none") && isKeyboardVisible()) {
                return doSwipeAction(mVolDownAction);
            }
            break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void reloadKeyboards() {
        mKeyboardSwitcher.setLanguageSwitcher(mLanguageSwitcher);
        updateKeyboardOptions();
        mKeyboardSwitcher.makeKeyboards(true);
    }

    private void postUpdateShiftKeyState() {
        // TODO(klausw): disabling, I have no idea what this is supposed to accomplish.
//        //updateShiftKeyState(getCurrentInputEditorInfo());
//
//        // FIXME: why the delay?
//        mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
//        // TODO: Should remove this 300ms delay?
//        mHandler.sendMessageDelayed(mHandler
//                .obtainMessage(MSG_UPDATE_SHIFT_STATE), 300);
    }

    public void updateShiftKeyState(EditorInfo attr) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && attr != null && mKeyboardSwitcher.isAlphabetMode()) {
            int oldState = getShiftState();
            boolean isShifted = mShiftKeyState.isChording();
            boolean isCapsLock = (oldState == Keyboard.SHIFT_CAPS_LOCKED || oldState == Keyboard.SHIFT_LOCKED);
            //Log.i(TAG, "updateShiftKeyState isShifted=" + isShifted + " isCapsLock=" + isCapsLock + " isMomentary=" + mShiftKeyState.isMomentary());
            int newState = Keyboard.SHIFT_OFF;
            if (isShifted) {
                newState = (mSavedShiftState == Keyboard.SHIFT_LOCKED) ? Keyboard.SHIFT_CAPS : Keyboard.SHIFT_ON;
            } else if (isCapsLock) {
                newState = getCapsOrShiftLockState();
            }
            //Log.i(TAG, "updateShiftKeyState " + oldState + " -> " + newState);
            mKeyboardSwitcher.setShiftState(newState);
        }
        if (ic != null) {
            // Clear modifiers other than shift, to avoid them getting stuck
            int states = 
                KeyEvent.META_FUNCTION_ON
                | KeyEvent.META_ALT_MASK
                | KeyEvent.META_CTRL_MASK
                | KeyEvent.META_META_MASK
                | KeyEvent.META_SYM_ON;
            ic.clearMetaKeyStates(states);
        }
    }

    private int getShiftState() {
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView view = mKeyboardSwitcher.getInputView();
            if (view != null) {
                return view.getShiftState();
            }
        }
        return Keyboard.SHIFT_OFF;
    }

    private boolean isShiftCapsMode() {
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView view = mKeyboardSwitcher.getInputView();
            if (view != null) {
                return view.isShiftCaps();
            }
        }
        return false;
    }

    private void swapPunctuationAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == ASCII_SPACE
                && isSentenceSeparator(lastTwo.charAt(1))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
            mJustAddedAutoSpace = true;
        }
    }

    private void reswapPeriodAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && lastThree.charAt(0) == ASCII_PERIOD
                && lastThree.charAt(1) == ASCII_SPACE
                && lastThree.charAt(2) == ASCII_PERIOD) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(3, 0);
            ic.commitText(" ..", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }

    private void maybeRemovePreviousPeriod(CharSequence text) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null || text.length() == 0)
            return;

        // When the text's first character is '.', remove the previous period
        // if there is one.
        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == ASCII_PERIOD
                && text.charAt(0) == ASCII_PERIOD) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void removeTrailingSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;

        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == ASCII_SPACE) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    private void showInputMethodPicker() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showInputMethodPicker();
    }

    private void onOptionKeyPressed() {
        if (!isShowingOptionDialog()) {
            showOptionsMenu();
        }
    }

    private void onOptionKeyLongPressed() {
        if (!isShowingOptionDialog()) {
            showInputMethodPicker();
        }
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    private boolean isConnectbot() {
        EditorInfo ei = getCurrentInputEditorInfo();
        String pkg = ei.packageName;
        if (ei == null || pkg == null) return false;
        return ((pkg.equalsIgnoreCase("org.connectbot")
            || pkg.equalsIgnoreCase("org.woltage.irssiconnectbot")
            || pkg.equalsIgnoreCase("com.pslib.connectbot")
            || pkg.equalsIgnoreCase("sk.vx.connectbot")
        ) && ei.inputType == 0); // FIXME
    }

    private int getMetaState(boolean shifted) {
        int meta = 0;
        if (shifted) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (mModCtrl) meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (mModAlt) meta |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (mModMeta) meta |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        return meta;
    }

    private void sendKeyDown(InputConnection ic, int key, int meta) {
        long now = System.currentTimeMillis();
        if (ic != null) ic.sendKeyEvent(new KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, key, 0, meta));
    }

    private void sendKeyUp(InputConnection ic, int key, int meta) {
        long now = System.currentTimeMillis();
        if (ic != null) ic.sendKeyEvent(new KeyEvent(
                now, now, KeyEvent.ACTION_UP, key, 0, meta));
    }

    private void sendModifiedKeyDownUp(int key, boolean shifted) {
        InputConnection ic = getCurrentInputConnection();
        int meta = getMetaState(shifted);
        sendModifierKeysDown(shifted);
        sendKeyDown(ic, key, meta);
        sendKeyUp(ic, key, meta);
        sendModifierKeysUp(shifted);
    }

    private boolean isShiftMod() {
        if (mShiftKeyState.isChording()) return true;
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView kb = mKeyboardSwitcher.getInputView();
            if (kb != null) return kb.isShiftAll();
        }
        return false;
    }

    private boolean delayChordingCtrlModifier() {
        return sKeyboardSettings.chordingCtrlKey == 0;
    }

    private boolean delayChordingAltModifier() {
        return sKeyboardSettings.chordingAltKey == 0;
    }

    private boolean delayChordingMetaModifier() {
        return sKeyboardSettings.chordingMetaKey == 0;
    }

    private void sendModifiedKeyDownUp(int key) {
        sendModifiedKeyDownUp(key, isShiftMod());
    }

    private void sendShiftKey(InputConnection ic, boolean isDown) {
        int key = KeyEvent.KEYCODE_SHIFT_LEFT;
        int meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendCtrlKey(InputConnection ic, boolean isDown, boolean chording) {
        if (chording && delayChordingCtrlModifier()) return;

        int key = sKeyboardSettings.chordingCtrlKey;
        if (key == 0) key = KeyEvent.KEYCODE_CTRL_LEFT;
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendAltKey(InputConnection ic, boolean isDown, boolean chording) {
        if (chording && delayChordingAltModifier()) return;

        int key = sKeyboardSettings.chordingAltKey;
        if (key == 0) key = KeyEvent.KEYCODE_ALT_LEFT;
        int meta = KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendMetaKey(InputConnection ic, boolean isDown, boolean chording) {
        if (chording && delayChordingMetaModifier()) return;

        int key = sKeyboardSettings.chordingMetaKey;
        if (key == 0) key = KeyEvent.KEYCODE_META_LEFT;
        int meta = KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        if (isDown) {
            sendKeyDown(ic, key, meta);
        } else {
            sendKeyUp(ic, key, meta);
        }
    }

    private void sendModifierKeysDown(boolean shifted) {
        InputConnection ic = getCurrentInputConnection();
        if (shifted) {
            //Log.i(TAG, "send SHIFT down");
            sendShiftKey(ic, true);
        }
        if (mModCtrl && (!mCtrlKeyState.isChording() || delayChordingCtrlModifier())) {
            sendCtrlKey(ic, true, false);
        }
        if (mModAlt && (!mAltKeyState.isChording() || delayChordingAltModifier())) {
            sendAltKey(ic, true, false);
        }
        if (mModMeta && (!mMetaKeyState.isChording() || delayChordingMetaModifier())) {
            sendMetaKey(ic, true, false);
        }
    }

    private void handleModifierKeysUp(boolean shifted, boolean sendKey) {
        InputConnection ic = getCurrentInputConnection();
        if (mModMeta && (!mMetaKeyState.isChording() || delayChordingMetaModifier())) {
            if (sendKey) sendMetaKey(ic, false, false);
            if (!mMetaKeyState.isChording()) setModMeta(false);
        }
        if (mModAlt && (!mAltKeyState.isChording() || delayChordingAltModifier())) {
            if (sendKey) sendAltKey(ic, false, false);
            if (!mAltKeyState.isChording()) setModAlt(false);
        }
        if (mModCtrl && (!mCtrlKeyState.isChording()  || delayChordingCtrlModifier())) {
            if (sendKey) sendCtrlKey(ic, false, false);
            if (!mCtrlKeyState.isChording()) setModCtrl(false);
        }
        if (shifted) {
            //Log.i(TAG, "send SHIFT up");
            if (sendKey) sendShiftKey(ic, false);
            int shiftState = getShiftState();
            if (!(mShiftKeyState.isChording() || shiftState == Keyboard.SHIFT_LOCKED)) {
                resetShift();
            }
        }
    }

    private void sendModifierKeysUp(boolean shifted) {
        handleModifierKeysUp(shifted, true);
    }

    private void sendSpecialKey(int code) {
        if (!isConnectbot()) {
            sendModifiedKeyDownUp(code);
            return;
        }

        // TODO(klausw): properly support xterm sequences for Ctrl/Alt modifiers?
        // See http://slackware.osuosl.org/slackware-12.0/source/l/ncurses/xterm.terminfo
        // and the output of "$ infocmp -1L". Support multiple sets, and optional 
        // true numpad keys?
        if (ESC_SEQUENCES == null) {
            ESC_SEQUENCES = new HashMap<Integer, String>();
            CTRL_SEQUENCES = new HashMap<Integer, Integer>();

            // VT escape sequences without leading Escape
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_HOME, "[1~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_END, "[4~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_PAGE_UP, "[5~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_PAGE_DOWN, "[6~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F1, "OP");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F2, "OQ");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F3, "OR");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F4, "OS");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F5, "[15~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F6, "[17~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F7, "[18~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F8, "[19~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F9, "[20~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F10, "[21~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F11, "[23~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F12, "[24~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FORWARD_DEL, "[3~");
            ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_INSERT, "[2~");

            // Special ConnectBot hack: Ctrl-1 to Ctrl-0 for F1-F10.
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F1, KeyEvent.KEYCODE_1);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F2, KeyEvent.KEYCODE_2);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F3, KeyEvent.KEYCODE_3);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F4, KeyEvent.KEYCODE_4);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F5, KeyEvent.KEYCODE_5);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F6, KeyEvent.KEYCODE_6);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F7, KeyEvent.KEYCODE_7);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F8, KeyEvent.KEYCODE_8);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F9, KeyEvent.KEYCODE_9);
            CTRL_SEQUENCES.put(-LatinKeyboardView.KEYCODE_FKEY_F10, KeyEvent.KEYCODE_0);

            // Natively supported by ConnectBot
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_DPAD_UP, "OA");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_DPAD_DOWN, "OB");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_DPAD_LEFT, "OD");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_DPAD_RIGHT, "OC");

            // No VT equivalents?
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_DPAD_CENTER, "");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_SYSRQ, "");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_BREAK, "");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_NUM_LOCK, "");
            // ESC_SEQUENCES.put(-LatinKeyboardView.KEYCODE_SCROLL_LOCK, "");
        }
        InputConnection ic = getCurrentInputConnection();
        Integer ctrlseq = null;
        if (mConnectbotTabHack) {
            ctrlseq = CTRL_SEQUENCES.get(code);
        }
        String seq = ESC_SEQUENCES.get(code);

        if (ctrlseq != null) {
            if (mModAlt) {
                // send ESC prefix for "Alt"
                ic.commitText(Character.toString((char) 27), 1);
            }
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    ctrlseq));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    ctrlseq));
        } else if (seq != null) {
            if (mModAlt) {
                // send ESC prefix for "Alt"
                ic.commitText(Character.toString((char) 27), 1);
            }
            // send ESC prefix of escape sequence
            ic.commitText(Character.toString((char) 27), 1);
            ic.commitText(seq, 1);
        } else {
            // send key code, let connectbot handle it
            sendDownUpKeyEvents(code);
        }
        handleModifierKeysUp(false, false);
    }

    private final static int asciiToKeyCode[] = new int[127];
    private final static int KF_MASK = 0xffff;
    private final static int KF_SHIFTABLE = 0x10000;
    private final static int KF_UPPER = 0x20000;
    private final static int KF_LETTER = 0x40000;

    {
        // Include RETURN in this set even though it's not printable.
        // Most other non-printable keys get handled elsewhere.
        asciiToKeyCode['\n'] = KeyEvent.KEYCODE_ENTER | KF_SHIFTABLE;

        // Non-alphanumeric ASCII codes which have their own keys
        // (on some keyboards)
        asciiToKeyCode[' '] = KeyEvent.KEYCODE_SPACE | KF_SHIFTABLE;
        //asciiToKeyCode['!'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['"'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['#'] = KeyEvent.KEYCODE_POUND;
        //asciiToKeyCode['$'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['%'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['&'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['\''] = KeyEvent.KEYCODE_APOSTROPHE;
        //asciiToKeyCode['('] = KeyEvent.KEYCODE_;
        //asciiToKeyCode[')'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['*'] = KeyEvent.KEYCODE_STAR;
        asciiToKeyCode['+'] = KeyEvent.KEYCODE_PLUS;
        asciiToKeyCode[','] = KeyEvent.KEYCODE_COMMA;
        asciiToKeyCode['-'] = KeyEvent.KEYCODE_MINUS;
        asciiToKeyCode['.'] = KeyEvent.KEYCODE_PERIOD;
        asciiToKeyCode['/'] = KeyEvent.KEYCODE_SLASH;
        //asciiToKeyCode[':'] = KeyEvent.KEYCODE_;
        asciiToKeyCode[';'] = KeyEvent.KEYCODE_SEMICOLON;
        //asciiToKeyCode['<'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['='] = KeyEvent.KEYCODE_EQUALS;
        //asciiToKeyCode['>'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['?'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['@'] = KeyEvent.KEYCODE_AT;
        asciiToKeyCode['['] = KeyEvent.KEYCODE_LEFT_BRACKET;
        asciiToKeyCode['\\'] = KeyEvent.KEYCODE_BACKSLASH;
        asciiToKeyCode[']'] = KeyEvent.KEYCODE_RIGHT_BRACKET;
        //asciiToKeyCode['^'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['_'] = KeyEvent.KEYCODE_;
        asciiToKeyCode['`'] = KeyEvent.KEYCODE_GRAVE;
        //asciiToKeyCode['{'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['|'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['}'] = KeyEvent.KEYCODE_;
        //asciiToKeyCode['~'] = KeyEvent.KEYCODE_;


        for (int i = 0; i <= 25; ++i) {
            asciiToKeyCode['a' + i] = KeyEvent.KEYCODE_A + i | KF_LETTER;
            asciiToKeyCode['A' + i] = KeyEvent.KEYCODE_A + i | KF_UPPER | KF_LETTER;
        }

        for (int i = 0; i <= 9; ++i) {
            asciiToKeyCode['0' + i] = KeyEvent.KEYCODE_0 + i;
        }
    }

    public void sendModifiableKeyChar(char ch) {
        // Support modified key events
        boolean modShift = isShiftMod();
        if ((modShift || mModCtrl || mModAlt || mModMeta) && ch > 0 && ch < 127) {
            InputConnection ic = getCurrentInputConnection();
            if (isConnectbot()) {
                if (mModAlt) {
                    // send ESC prefix
                    ic.commitText(Character.toString((char) 27), 1);
                }
                if (mModCtrl) {
                    int code = ch & 31;
                    if (code == 9) {
                        sendTab();
                    } else {
                        ic.commitText(Character.toString((char) code), 1);
                    }
                } else {
                    ic.commitText(Character.toString(ch), 1);
                }
                handleModifierKeysUp(false, false);
                return;
            }

            // Non-ConnectBot

            // Restrict Shift modifier to ENTER and SPACE, supporting Shift-Enter etc.
            // Note that most special keys such as DEL or cursor keys aren't handled
            // by this charcode-based method.

            int combinedCode = asciiToKeyCode[ch];
            if (combinedCode > 0) {
                int code = combinedCode & KF_MASK;
                boolean shiftable = (combinedCode & KF_SHIFTABLE) > 0;
                boolean upper = (combinedCode & KF_UPPER) > 0;
                boolean letter = (combinedCode & KF_LETTER) > 0;
                boolean shifted = modShift && (upper || shiftable);
                if (letter && !mModCtrl && !mModAlt && !mModMeta) {
                    // Try workaround for issue 179 where letters don't get upcased
                    ic.commitText(Character.toString(ch), 1);
                    handleModifierKeysUp(false, false);
                } else {
                    sendModifiedKeyDownUp(code, shifted);
                }
                return;
            }
        }

        if (ch >= '0' && ch <= '9') {
            //WIP
            InputConnection ic = getCurrentInputConnection();
            ic.clearMetaKeyStates(KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON | KeyEvent.META_SYM_ON);
            //EditorInfo ei = getCurrentInputEditorInfo();
            //sendModifiedKeyDownUp(KeyEvent.KEYCODE_0 + ch - '0');
            //return;
        }

        // Default handling for anything else, including unmodified ENTER and SPACE.
        sendKeyChar(ch);
    }
    
    private void sendTab() {
        InputConnection ic = getCurrentInputConnection();
        boolean tabHack = isConnectbot() && mConnectbotTabHack;

        // FIXME: tab and ^I don't work in connectbot, hackish workaround
        if (tabHack) {
            if (mModAlt) {
                // send ESC prefix
                ic.commitText(Character.toString((char) 27), 1);
            }
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_I));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_I));
        } else {
            sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB);
        }
    }

    private void sendEscape() {
        if (isConnectbot()) {
            sendKeyChar((char) 27);
        } else {
            sendModifiedKeyDownUp(111 /*KeyEvent.KEYCODE_ESCAPE */);
        }
    }

    private boolean processMultiKey(int primaryCode) {
        if (mDeadAccentBuffer.composeBuffer.length() > 0) {
            //Log.i(TAG, "processMultiKey: pending DeadAccent, length=" + mDeadAccentBuffer.composeBuffer.length());
            mDeadAccentBuffer.execute(primaryCode);
            mDeadAccentBuffer.clear();
            return true;
        }
        if (mComposeMode) {
            mComposeMode = mComposeBuffer.execute(primaryCode);
            return true;
        }
        return false;
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
        long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.KEYCODE_DELETE
                || when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        final boolean distinctMultiTouch = mKeyboardSwitcher
                .hasDistinctMultitouch();
        switch (primaryCode) {
        case Keyboard.KEYCODE_DELETE:
            if (processMultiKey(primaryCode)) {
                break;
            }
            handleBackspace();
            mDeleteCount++;
            LatinImeLogger.logOnDelete();
            break;
        case Keyboard.KEYCODE_SHIFT:
            // Shift key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                handleShift();
            break;
        case Keyboard.KEYCODE_MODE_CHANGE:
            // Symbol key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                changeKeyboardMode();
            break;
        case LatinKeyboardView.KEYCODE_CTRL_LEFT:
            // Ctrl key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                setModCtrl(!mModCtrl);
            break;
        case LatinKeyboardView.KEYCODE_ALT_LEFT:
            // Alt key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                setModAlt(!mModAlt);
            break;
        case LatinKeyboardView.KEYCODE_META_LEFT:
            // Meta key is handled in onPress() when device has distinct
            // multi-touch panel.
            if (!distinctMultiTouch)
                setModMeta(!mModMeta);
            break;
        case LatinKeyboardView.KEYCODE_FN:
            if (!distinctMultiTouch)
                setModFn(!mModFn);
            break;
        case Keyboard.KEYCODE_CANCEL:
            if (!isShowingOptionDialog()) {
                handleClose();
            }
            break;
        case LatinKeyboardView.KEYCODE_OPTIONS:
            onOptionKeyPressed();
            break;
        case LatinKeyboardView.KEYCODE_OPTIONS_LONGPRESS:
            onOptionKeyLongPressed();
            break;
        case LatinKeyboardView.KEYCODE_COMPOSE:
            mComposeMode = !mComposeMode;
            mComposeBuffer.clear();
            break;
        case LatinKeyboardView.KEYCODE_NEXT_LANGUAGE:
            toggleLanguage(false, true);
            break;
        case LatinKeyboardView.KEYCODE_PREV_LANGUAGE:
            toggleLanguage(false, false);
            break;
        case 9 /* Tab */:
            if (processMultiKey(primaryCode)) {
                break;
            }
            sendTab();
            break;
        case LatinKeyboardView.KEYCODE_ESCAPE:
            if (processMultiKey(primaryCode)) {
                break;
            }
            sendEscape();
            break;
        case LatinKeyboardView.KEYCODE_DPAD_UP:
        case LatinKeyboardView.KEYCODE_DPAD_DOWN:
        case LatinKeyboardView.KEYCODE_DPAD_LEFT:
        case LatinKeyboardView.KEYCODE_DPAD_RIGHT:
        case LatinKeyboardView.KEYCODE_DPAD_CENTER:
        case LatinKeyboardView.KEYCODE_HOME:
        case LatinKeyboardView.KEYCODE_END:
        case LatinKeyboardView.KEYCODE_PAGE_UP:
        case LatinKeyboardView.KEYCODE_PAGE_DOWN:
        case LatinKeyboardView.KEYCODE_FKEY_F1:
        case LatinKeyboardView.KEYCODE_FKEY_F2:
        case LatinKeyboardView.KEYCODE_FKEY_F3:
        case LatinKeyboardView.KEYCODE_FKEY_F4:
        case LatinKeyboardView.KEYCODE_FKEY_F5:
        case LatinKeyboardView.KEYCODE_FKEY_F6:
        case LatinKeyboardView.KEYCODE_FKEY_F7:
        case LatinKeyboardView.KEYCODE_FKEY_F8:
        case LatinKeyboardView.KEYCODE_FKEY_F9:
        case LatinKeyboardView.KEYCODE_FKEY_F10:
        case LatinKeyboardView.KEYCODE_FKEY_F11:
        case LatinKeyboardView.KEYCODE_FKEY_F12:
        case LatinKeyboardView.KEYCODE_FORWARD_DEL:
        case LatinKeyboardView.KEYCODE_INSERT:
        case LatinKeyboardView.KEYCODE_SYSRQ:
        case LatinKeyboardView.KEYCODE_BREAK:
        case LatinKeyboardView.KEYCODE_NUM_LOCK:
        case LatinKeyboardView.KEYCODE_SCROLL_LOCK:
            if (processMultiKey(primaryCode)) {
                break;
            }
            // send as plain keys, or as escape sequence if needed
            sendSpecialKey(-primaryCode);
            break;
        default:
            if (!mComposeMode && mDeadKeysActive && Character.getType(primaryCode) == Character.NON_SPACING_MARK) {
                //Log.i(TAG, "possible dead character: " + primaryCode);
                if (!mDeadAccentBuffer.execute(primaryCode)) {
                    //Log.i(TAG, "double dead key");
                    break; // pressing a dead key twice produces spacing equivalent
                }
                updateShiftKeyState(getCurrentInputEditorInfo());
                break;
            }
            if (processMultiKey(primaryCode)) {
                break;
            }
            if (primaryCode != ASCII_ENTER) {
                mJustAddedAutoSpace = false;
            }
            RingCharBuffer.getInstance().push((char) primaryCode, x, y);
            LatinImeLogger.logOnInputChar();
            if (isWordSeparator(primaryCode)) {
                handleSeparator(primaryCode);
            } else {
                handleCharacter(primaryCode, keyCodes);
            }
            // Cancel the just reverted state
            mJustRevertedSeparator = null;
        }
        mKeyboardSwitcher.onKey(primaryCode);
        // Reset after any single keystroke
        mEnteredText = null;
        //mDeadAccentBuffer.clear();  // FIXME
    }

    public void onText(CharSequence text) {
        //mDeadAccentBuffer.clear();  // FIXME
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        abortCorrection(false);
        ic.beginBatchEdit();
        maybeRemovePreviousPeriod(text);
        ic.commitText(text, 1);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
        mKeyboardSwitcher.onKey(0); // dummy key code.
        mJustRevertedSeparator = null;
        mJustAddedAutoSpace = false;
        mEnteredText = text;
    }

    public void onCancel() {
        // User released a finger outside any key
        mKeyboardSwitcher.onCancelInput();
    }

    private void handleBackspace() {
        boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;

        ic.beginBatchEdit();

            deleteChar = true;
        postUpdateShiftKeyState();
        TextEntryState.backspace();
        if (TextEntryState.getState() == TextEntryState.State.UNDO_COMMIT) {
            revertLastWord(deleteChar);
            ic.endBatchEdit();
            return;
        } else if (mEnteredText != null
                && sameAsTextBeforeCursor(ic, mEnteredText)) {
            ic.deleteSurroundingText(mEnteredText.length(), 0);
        } else if (deleteChar) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                if (mDeleteCount > DELETE_ACCELERATE_AT) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
        }
        mJustRevertedSeparator = null;
        ic.endBatchEdit();
    }

    private void setModCtrl(boolean val) {
        // Log.i("LatinIME", "setModCtrl "+ mModCtrl + "->" + val + ", chording=" + mCtrlKeyState.isChording());
        mKeyboardSwitcher.setCtrlIndicator(val);
        mModCtrl = val;
    }

    private void setModAlt(boolean val) {
        //Log.i("LatinIME", "setModAlt "+ mModAlt + "->" + val + ", chording=" + mAltKeyState.isChording());
        mKeyboardSwitcher.setAltIndicator(val);
        mModAlt = val;
    }

    private void setModMeta(boolean val) {
        //Log.i("LatinIME", "setModMeta "+ mModMeta + "->" + val + ", chording=" + mMetaKeyState.isChording());
        mKeyboardSwitcher.setMetaIndicator(val);
        mModMeta = val;
    }

    private void setModFn(boolean val) {
        //Log.i("LatinIME", "setModFn " + mModFn + "->" + val + ", chording=" + mFnKeyState.isChording());
        mModFn = val;
        mKeyboardSwitcher.setFn(val);
        mKeyboardSwitcher.setCtrlIndicator(mModCtrl);
        mKeyboardSwitcher.setAltIndicator(mModAlt);
        mKeyboardSwitcher.setMetaIndicator(mModMeta);
    }

    private void startMultitouchShift() {
        int newState = Keyboard.SHIFT_ON;
        if (mKeyboardSwitcher.isAlphabetMode()) {
            mSavedShiftState = getShiftState();
            if (mSavedShiftState == Keyboard.SHIFT_LOCKED) newState = Keyboard.SHIFT_CAPS;
        }
        handleShiftInternal(true, newState);
    }

    private void commitMultitouchShift() {
        if (mKeyboardSwitcher.isAlphabetMode()) {
            int newState = nextShiftState(mSavedShiftState, true);
            handleShiftInternal(true, newState);
        } else {
            // do nothing, keyboard is already flipped
        }
    }

    private void resetMultitouchShift() {
        int newState = Keyboard.SHIFT_OFF;
        if (mSavedShiftState == Keyboard.SHIFT_CAPS_LOCKED || mSavedShiftState == Keyboard.SHIFT_LOCKED) {
            newState = mSavedShiftState;
        }
        handleShiftInternal(true, newState);
    }

    private void resetShift() {
        handleShiftInternal(true, Keyboard.SHIFT_OFF);
    }

    private void handleShift() {
        handleShiftInternal(false, -1);
    }

    private static int getCapsOrShiftLockState() {
        return sKeyboardSettings.capsLock ? Keyboard.SHIFT_CAPS_LOCKED : Keyboard.SHIFT_LOCKED;
    }
    
    // Rotate through shift states by successively pressing and releasing the Shift key.
    private static int nextShiftState(int prevState, boolean allowCapsLock) {
        if (allowCapsLock) {
            if (prevState == Keyboard.SHIFT_OFF) {
                return Keyboard.SHIFT_ON;
            } else if (prevState == Keyboard.SHIFT_ON) {
                return getCapsOrShiftLockState();
            } else {
                return Keyboard.SHIFT_OFF;
            }
        } else {
            // currently unused, see toggleShift()
            if (prevState == Keyboard.SHIFT_OFF) {
                return Keyboard.SHIFT_ON;
            } else {
                return Keyboard.SHIFT_OFF;
            }
        }
    }

    private void handleShiftInternal(boolean forceState, int newState) {
        //Log.i(TAG, "handleShiftInternal forceNormal=" + forceNormal);
        mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isAlphabetMode()) {
            if (forceState) {
                switcher.setShiftState(newState);
            } else {
                switcher.setShiftState(nextShiftState(getShiftState(), true));
            }
        } else {
            switcher.toggleShift();
        }
    }

    private void abortCorrection(boolean force) {
        if (force || TextEntryState.isCorrecting()) {
            getCurrentInputConnection().finishComposingText();
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (mLastSelectionStart == mLastSelectionEnd
                && TextEntryState.isCorrecting()) {
            abortCorrection(false);
        }

            sendModifiableKeyChar((char) primaryCode);
        updateShiftKeyState(getCurrentInputEditorInfo());
        if (LatinIME.PERF_DEBUG)
            measureCps();
        TextEntryState.typedCharacter((char) primaryCode,
                isWordSeparator(primaryCode));
    }

    private void handleSeparator(int primaryCode) {
        // Handle separator
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            abortCorrection(false);
        }
        if (mJustAddedAutoSpace && primaryCode == ASCII_ENTER) {
            removeTrailingSpace();
            mJustAddedAutoSpace = false;
        }
        sendModifiableKeyChar((char) primaryCode);

        // Handle the case of ". ." -> " .." with auto-space if necessary
        // before changing the TextEntryState.
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
                && primaryCode == ASCII_PERIOD) {
            reswapPeriodAndSpace();
        }

        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
                && primaryCode != ASCII_ENTER) {
            swapPunctuationAndSpace();
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    private void handleClose() {
        requestHideSelf(0);
        if (mKeyboardSwitcher != null) {
            LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
            if (inputView != null) {
                inputView.closing();
            }
        }
        TextEntryState.endSession();
    }

    private boolean isCursorTouchingWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return false;
        CharSequence toLeft = ic.getTextBeforeCursor(1, 0);
        CharSequence toRight = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(toLeft) && !isWordSeparator(toLeft.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(toRight) && !isWordSeparator(toRight.charAt(0))) {
            return true;
        }
        return false;
    }

    private boolean sameAsTextBeforeCursor(InputConnection ic, CharSequence text) {
        CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    public void revertLastWord(boolean deleteChar) {
        final int length = mComposing.length();
        if (length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            mPredicting = true;
            mJustRevertedSeparator = ic.getTextBeforeCursor(1, 0);
            if (deleteChar)
                ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            CharSequence toTheLeft = ic
                    .getTextBeforeCursor(mCommittedLength, 0);
            if (toTheLeft != null && toTheLeft.length() > 0
                    && isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            ic.setComposingText(mComposing, 1);
            TextEntryState.backspace();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mJustRevertedSeparator = null;
        }
    }

    protected String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char) code));
    }

    private boolean isSentenceSeparator(int code) {
        return mSentenceSeparators.contains(String.valueOf((char) code));
    }

    private void sendSpace() {
        sendModifiableKeyChar((char) ASCII_SPACE);
        updateShiftKeyState(getCurrentInputEditorInfo());
        // onKey(KEY_SPACE[0], KEY_SPACE);
    }

    void toggleLanguage(boolean reset, boolean next) {
        if (reset) {
            mLanguageSwitcher.reset();
        } else {
            if (next) {
                mLanguageSwitcher.next();
            } else {
                mLanguageSwitcher.prev();
            }
        }
        int currentKeyboardMode = mKeyboardSwitcher.getKeyboardMode();
        reloadKeyboards();
        mKeyboardSwitcher.makeKeyboards(true);
        mKeyboardSwitcher.setKeyboardMode(currentKeyboardMode, 0);
        initSuggest(mLanguageSwitcher.getInputLanguage());
        mLanguageSwitcher.persist();
        mDeadKeysActive = mLanguageSwitcher.allowDeadKeys();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Log.i(TAG, "onSharedPreferenceChanged()");
        boolean needReload = false;
        Resources res = getResources();
        
        // Apply globally handled shared prefs
        sKeyboardSettings.sharedPreferenceChanged(sharedPreferences, key);
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_NEED_RELOAD)) {
            needReload = true;
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RECREATE_INPUT_VIEW)) {
            mKeyboardSwitcher.recreateInputView();
        }
        if (sKeyboardSettings.hasFlag(GlobalKeyboardSettings.FLAG_PREF_RESET_KEYBOARDS)) {
            toggleLanguage(true, true);
        }
        int unhandledFlags = sKeyboardSettings.unhandledFlags();
        if (unhandledFlags != GlobalKeyboardSettings.FLAG_PREF_NONE) {
            Log.w(TAG, "Not all flag settings handled, remaining=" + unhandledFlags);
        }

        if (PREF_SELECTED_LANGUAGES.equals(key)) {
            mLanguageSwitcher.loadLocales(sharedPreferences);
            mRefreshKeyboardRequired = true;
        } else if (PREF_CONNECTBOT_TAB_HACK.equals(key)) {
            mConnectbotTabHack = sharedPreferences.getBoolean(
                    PREF_CONNECTBOT_TAB_HACK, res
                            .getBoolean(R.bool.default_connectbot_tab_hack));
        } else if (PREF_FULLSCREEN_OVERRIDE.equals(key)) {
            mFullscreenOverride = sharedPreferences.getBoolean(
                    PREF_FULLSCREEN_OVERRIDE, res
                            .getBoolean(R.bool.default_fullscreen_override));
            needReload = true;
        } else if (PREF_FORCE_KEYBOARD_ON.equals(key)) {
            mForceKeyboardOn = sharedPreferences.getBoolean(
                    PREF_FORCE_KEYBOARD_ON, res
                            .getBoolean(R.bool.default_force_keyboard_on));
            needReload = true;
        } else if (PREF_KEYBOARD_NOTIFICATION.equals(key)) {
            mKeyboardNotification = sharedPreferences.getBoolean(
                    PREF_KEYBOARD_NOTIFICATION, res
                            .getBoolean(R.bool.default_keyboard_notification));
            setNotification(mKeyboardNotification);
        } else if (PREF_HEIGHT_PORTRAIT.equals(key)) {
            mHeightPortrait = getHeight(sharedPreferences,
                    PREF_HEIGHT_PORTRAIT, res.getString(R.string.default_height_portrait));
            needReload = true;
        } else if (PREF_HEIGHT_LANDSCAPE.equals(key)) {
            mHeightLandscape = getHeight(sharedPreferences,
                    PREF_HEIGHT_LANDSCAPE, res.getString(R.string.default_height_landscape));
            needReload = true;
        } else if (PREF_HINT_MODE.equals(key)) {
            LatinIME.sKeyboardSettings.hintMode = Integer.parseInt(sharedPreferences.getString(PREF_HINT_MODE,
                    res.getString(R.string.default_hint_mode)));
            needReload = true;
        } else if (PREF_LONGPRESS_TIMEOUT.equals(key)) {
               LatinIME.sKeyboardSettings.longpressTimeout = getPrefInt(sharedPreferences, PREF_LONGPRESS_TIMEOUT,
                       res.getString(R.string.default_long_press_duration));
        } else if (PREF_RENDER_MODE.equals(key)) {
            LatinIME.sKeyboardSettings.renderMode = getPrefInt(sharedPreferences, PREF_RENDER_MODE,
                    res.getString(R.string.default_render_mode));
            needReload = true;
        } else if (PREF_SWIPE_UP.equals(key)) {
            mSwipeUpAction = sharedPreferences.getString(PREF_SWIPE_UP, res.getString(R.string.default_swipe_up));
        } else if (PREF_SWIPE_DOWN.equals(key)) {
            mSwipeDownAction = sharedPreferences.getString(PREF_SWIPE_DOWN, res.getString(R.string.default_swipe_down));
        } else if (PREF_SWIPE_LEFT.equals(key)) {
            mSwipeLeftAction = sharedPreferences.getString(PREF_SWIPE_LEFT, res.getString(R.string.default_swipe_left));
        } else if (PREF_SWIPE_RIGHT.equals(key)) {
            mSwipeRightAction = sharedPreferences.getString(PREF_SWIPE_RIGHT, res.getString(R.string.default_swipe_right));
        } else if (PREF_VOL_UP.equals(key)) {
            mVolUpAction = sharedPreferences.getString(PREF_VOL_UP, res.getString(R.string.default_vol_up));
        } else if (PREF_VOL_DOWN.equals(key)) {
            mVolDownAction = sharedPreferences.getString(PREF_VOL_DOWN, res.getString(R.string.default_vol_down));
        }

        updateKeyboardOptions();
        if (needReload) {
            mKeyboardSwitcher.makeKeyboards(true);
        }
    }

    private boolean doSwipeAction(String action) {
        //Log.i(TAG, "doSwipeAction + " + action);
        if (action == null || action.equals("") || action.equals("none")) {
            return false;
        } else if (action.equals("close")) {
            handleClose();
        } else if (action.equals("settings")) {
            launchSettings();
        } else if (action.equals("lang_prev")) {
            toggleLanguage(false, false);
        } else if (action.equals("lang_next")) {
            toggleLanguage(false, true);
        } else if (action.equals("debug_auto_play")) {
            if (LatinKeyboardView.DEBUG_AUTO_PLAY) {
                ClipboardManager cm = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE));
                CharSequence text = cm.getText();
                if (!TextUtils.isEmpty(text)) {
                    mKeyboardSwitcher.getInputView().startPlaying(text.toString());
                }
            }
        } else if (action.equals("height_up")) {
            if (isPortrait()) {
                mHeightPortrait += 5;
                if (mHeightPortrait > 70) mHeightPortrait = 70;
            } else {
                mHeightLandscape += 5;
                if (mHeightLandscape > 70) mHeightLandscape = 70;                
            }
            toggleLanguage(true, true);
        } else if (action.equals("height_down")) {
            if (isPortrait()) {
                mHeightPortrait -= 5;
                if (mHeightPortrait < 15) mHeightPortrait = 15;
            } else {
                mHeightLandscape -= 5;
                if (mHeightLandscape < 15) mHeightLandscape = 15;                
            }
            toggleLanguage(true, true);
        } else {
            Log.i(TAG, "Unsupported swipe action config: " + action);
        }
        return true;
    }

    public boolean swipeRight() {
        return doSwipeAction(mSwipeRightAction);
    }

    public boolean swipeLeft() {
        return doSwipeAction(mSwipeLeftAction);
    }

    public boolean swipeDown() {
        return doSwipeAction(mSwipeDownAction);
    }

    public boolean swipeUp() {
        return doSwipeAction(mSwipeUpAction);
    }

    public void onPress(int primaryCode) {
        InputConnection ic = getCurrentInputConnection();
        if (mKeyboardSwitcher.isSoundFeedbackRequired()) {
            playKeyClick(primaryCode);
        }
        final boolean distinctMultiTouch = mKeyboardSwitcher
                .hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.KEYCODE_SHIFT) {
            mShiftKeyState.onPress();
            startMultitouchShift();
        } else if (distinctMultiTouch
                && primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            changeKeyboardMode();
            mSymbolKeyState.onPress();
            mKeyboardSwitcher.setAutoModeSwitchStateMomentary();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_CTRL_LEFT) {
            setModCtrl(!mModCtrl);
            mCtrlKeyState.onPress();
            sendCtrlKey(ic, true, true);
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_ALT_LEFT) {
            setModAlt(!mModAlt);
            mAltKeyState.onPress();
            sendAltKey(ic, true, true);
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_META_LEFT) {
            setModMeta(!mModMeta);
            mMetaKeyState.onPress();
            sendMetaKey(ic, true, true);
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_FN) {
            setModFn(!mModFn);
            mFnKeyState.onPress();
        } else {
            mShiftKeyState.onOtherKeyPressed();
            mSymbolKeyState.onOtherKeyPressed();
            mCtrlKeyState.onOtherKeyPressed();
            mAltKeyState.onOtherKeyPressed();
            mMetaKeyState.onOtherKeyPressed();
            mFnKeyState.onOtherKeyPressed();
        }
    }

    public void onRelease(int primaryCode) {
        // Reset any drag flags in the keyboard
        ((LatinKeyboard) mKeyboardSwitcher.getInputView().getKeyboard())
                .keyReleased();
        final boolean distinctMultiTouch = mKeyboardSwitcher
                .hasDistinctMultitouch();
        InputConnection ic = getCurrentInputConnection();
        if (distinctMultiTouch && primaryCode == Keyboard.KEYCODE_SHIFT) {
            if (mShiftKeyState.isChording()) {
                resetMultitouchShift();
            } else {
                commitMultitouchShift();
            }
            mShiftKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            // Snap back to the previous keyboard mode if the user chords the
            // mode change key and
            // other key, then released the mode change key.
            if (mKeyboardSwitcher.isInChordingAutoModeSwitchState())
                changeKeyboardMode();
            mSymbolKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_CTRL_LEFT) {
            if (mCtrlKeyState.isChording()) {
                setModCtrl(false);
            }
            sendCtrlKey(ic, false, true);
            mCtrlKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_ALT_LEFT) {
            if (mAltKeyState.isChording()) {
                setModAlt(false);
            }
            sendAltKey(ic, false, true);
            mAltKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_META_LEFT) {
            if (mMetaKeyState.isChording()) {
                setModMeta(false);
            }
            sendMetaKey(ic, false, true);
            mMetaKeyState.onRelease();
        } else if (distinctMultiTouch
                && primaryCode == LatinKeyboardView.KEYCODE_FN) {
            if (mFnKeyState.isChording()) {
                setModFn(false);
            }
            mFnKeyState.onRelease();
        }
        // WARNING: Adding a chording modifier key? Make sure you also
        // edit PointerTracker.isModifierInternal(), otherwise it will
        // force a release event instead of chording.
    }

    // receive ringer mode changes to detect silent mode
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRingerMode();
        }
    };

    // update flags for silent mode
    private void updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            mSilentMode = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private float getKeyClickVolume() {
        if (mAudioManager == null) return 0.0f; // shouldn't happen
        
        // The volume calculations are poorly documented, this is the closest I could
        // find for explaining volume conversions:
        // http://developer.android.com/reference/android/media/MediaPlayer.html#setAuxEffectSendLevel(float)
        //
        //   Note that the passed level value is a raw scalar. UI controls should be scaled logarithmically:
        //   the gain applied by audio framework ranges from -72dB to 0dB, so an appropriate conversion 
        //   from linear UI input x to level is: x == 0 -> level = 0 0 < x <= R -> level = 10^(72*(x-R)/20/R)
        
        int method = sKeyboardSettings.keyClickMethod; // See click_method_values in strings.xml
        if (method == 0) return FX_VOLUME;
        
        float targetVol = sKeyboardSettings.keyClickVolume;

        if (method > 1) {
            // TODO(klausw): on some devices the media volume controls the click volume?
            // If that's the case, try to set a relative target volume.
            int mediaMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int mediaVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            //Log.i(TAG, "getKeyClickVolume relative, media vol=" + mediaVol + "/" + mediaMax);
            float channelVol = (float) mediaVol / mediaMax;
            if (method == 2) {
                targetVol *= channelVol;
            } else if (method == 3) {
                if (channelVol == 0) return 0.0f; // Channel is silent, won't get audio
                targetVol = Math.min(targetVol / channelVol, 1.0f); // Cap at 1.0
            }
        }
        // Set absolute volume, treating the percentage as a logarithmic control
        float vol = (float) Math.pow(10.0, FX_VOLUME_RANGE_DB * (targetVol - 1) / 20);
        //Log.i(TAG, "getKeyClickVolume absolute, target=" + targetVol + " amp=" + vol);
        return vol;
    }
    
    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we don't have the ringer state yet
        // mAudioManager will be set by updateRingerMode
        if (mAudioManager == null) {
            if (mKeyboardSwitcher.getInputView() != null) {
                updateRingerMode();
            }
        }
        if (mSoundOn && !mSilentMode) {
            // FIXME: Volume and enable should come from UI settings
            // FIXME: These should be triggered after auto-repeat logic
            int sound = AudioManager.FX_KEYPRESS_STANDARD;
            switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                sound = AudioManager.FX_KEYPRESS_DELETE;
                break;
            case ASCII_ENTER:
                sound = AudioManager.FX_KEYPRESS_RETURN;
                break;
            case ASCII_SPACE:
                sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                break;
            }
            mAudioManager.playSoundEffect(sound, getKeyClickVolume());
        }
    }

    /* package */boolean getPopupOn() {
        return mPopupOn;
    }

    protected void launchSettings() {
        launchSettings(LatinIMESettings.class);
    }

    public void launchDebugSettings() {
        launchSettings(LatinIMEDebugSettings.class);
    }

    protected void launchSettings(
            Class<? extends PreferenceActivity> settingsClass) {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, settingsClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void loadSettings() {
        // Get the settings preferences
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(this);
        mSoundOn = sp.getBoolean(PREF_SOUND_ON, false);
        mPopupOn = sp.getBoolean(PREF_POPUP_ON, mResources
                .getBoolean(R.bool.default_popup_preview));

        mLanguageSwitcher.loadLocales(sp);
        mDeadKeysActive = mLanguageSwitcher.allowDeadKeys();
    }

    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_dialog_keyboard);
        builder.setNegativeButton(android.R.string.cancel, null);
        CharSequence itemSettings = getString(R.string.english_ime_settings);
        CharSequence itemInputMethod = getString(R.string.selectInputMethod);
        builder.setItems(new CharSequence[] { itemInputMethod, itemSettings },
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface di, int position) {
                        di.dismiss();
                        switch (position) {
                        case POS_SETTINGS:
                            launchSettings();
                            break;
                        case POS_METHOD:
                            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                                    .showInputMethodPicker();
                            break;
                        }
                    }
                });
        builder.setTitle(mResources
                .getString(R.string.english_ime_input_options));
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mKeyboardSwitcher.getInputView().getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
    }

    public void changeKeyboardMode() {
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isAlphabetMode()) {
            mSavedShiftState = getShiftState();
        }
        switcher.toggleSymbols();
        if (switcher.isAlphabetMode()) {
            switcher.setShiftState(mSavedShiftState);
        }

        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    public static <E> ArrayList<E> newArrayList(E... elements) {
        int capacity = (elements.length * 110) / 100 + 5;
        ArrayList<E> list = new ArrayList<E>(capacity);
        Collections.addAll(list, elements);
        return list;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  Keyboard mode = " + mKeyboardSwitcher.getKeyboardMode());
        p.println("  mComposing=" + mComposing.toString());
        p.println("  mAutoSpace=" + mAutoSpace);
        p.println("  TextEntryState.state=" + TextEntryState.getState());
        p.println("  mSoundOn=" + mSoundOn);
        p.println("  mPopupOn=" + mPopupOn);
    }

    // Characters per second measurement

    private long mLastCpsTime;
    private static final int CPS_BUFFER_SIZE = 16;
    private long[] mCpsIntervals = new long[CPS_BUFFER_SIZE];
    private int mCpsIndex;
    private static Pattern NUMBER_RE = Pattern.compile("(\\d+).*");

    private void measureCps() {
        long now = System.currentTimeMillis();
        if (mLastCpsTime == 0)
            mLastCpsTime = now - 100; // Initial
        mCpsIntervals[mCpsIndex] = now - mLastCpsTime;
        mLastCpsTime = now;
        mCpsIndex = (mCpsIndex + 1) % CPS_BUFFER_SIZE;
        long total = 0;
        for (int i = 0; i < CPS_BUFFER_SIZE; i++)
            total += mCpsIntervals[i];
        System.out.println("CPS = " + ((CPS_BUFFER_SIZE * 1000f) / total));
    }

    public void onAutoCompletionStateChanged(boolean isAutoCompletion) {
        mKeyboardSwitcher.onAutoCompletionStateChanged(isAutoCompletion);
    }

    static int getIntFromString(String val, int defVal) {
        Matcher num = NUMBER_RE.matcher(val);
        if (!num.matches()) return defVal;
        return Integer.parseInt(num.group(1));
    }

    static int getPrefInt(SharedPreferences prefs, String prefName, int defVal) {
        String prefVal = prefs.getString(prefName, Integer.toString(defVal));
        //Log.i(TAG, "getPrefInt " + prefName + " = " + prefVal + ", default " + defVal);
        return getIntFromString(prefVal, defVal);
    }

    static int getPrefInt(SharedPreferences prefs, String prefName, String defStr) {
        int defVal = getIntFromString(defStr, 0);
        return getPrefInt(prefs, prefName, defVal);
    }

    static int getHeight(SharedPreferences prefs, String prefName, String defVal) {
        int val = getPrefInt(prefs, prefName, defVal);
        if (val < 15)
            val = 15;
        if (val > 75)
            val = 75;
        return val;
    }
}
