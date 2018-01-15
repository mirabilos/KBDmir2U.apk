## KBDmir2U.apk ##

This is currently just a fork of [Hacker’s Keyboard](https://github.com/klausw/hackerskeyboard) but will eventually deviate:
* No vibration (⚠)
* No word suggestions, completion, etc.
* No dictionaries (meaning no JNI code any more, Pure Java™ code)
* No automatic capitalisation, etc.
* Support for only one locale (English as spoken in the EU, with German accent *ahem*)
* Use of the [KBDmir2U](http://fish.mirbsd.org/~tg/pub/kbd.png) layout
* No more pop-ups, just using the `Mode_switch` and `Shift` keys
* Changing keycaps depending on `Ctrl`, `Mode_switch` and `Shift` state
* Usable as system IME (i.e. during storage encryption unlock)
* Much reduced resource footprint (and, hopefully, bugs count)

We’ll get there slowly, if at all. Bear with me for the ride, I don’t actually know Java™, especially not in the Android environment. Until then, most of the old documentation here is kept around, even if it’s stale in the upstream of the fork already.

### More KBDmir2U ###

* [http://www.mirbsd.org/cvs.cgi/contrib/samples/dot.Xmodmap?f=u&only_with_tag=grml&logsort=date](`~/.Xmodmap` for X.org/`evdev`)
* [http://www.mirbsd.org/cvs.cgi/contrib/samples/dot.Xmodmap?rev=HEAD](`~/.Xmodmap` for XFree86®/nōn-`evdev` input)
* [http://www.mirbsd.org/cvs.cgi/contrib/code/Snippets/KBDmir2U.map?rev=HEAD](keymap for the Linux text console)
* [https://fsinfo.noone.org/~abe/typing-8bit.html#not](the BSD `wscons(4)` text console supports this out of the box)
* [http://www.mirbsd.org/cvs.cgi/contrib/code/Snippets/KBDmir2U.exe?rev=HEAD](self-extracting LHarc archive with keymap for Microsoft® NT® 4 or newer)
* [http://www.mirbsd.org/cvs.cgi/contrib/samples/kbd-layout?rev=HEAD](keyboard layout documentation), also [http://www.mirbsd.org/cvs.cgi/contrib/samples/kbd-xf86?rev=HEAD](source code) for the [http://fish.mirbsd.org/~tg/pub/kbd.png](above documentation picture)

## Overview ##

Are you missing the key layout you're used to from your computer when using an Android device? This software keyboard has separate number keys, punctuation in the usual places, and arrow keys. It is based on the AOSP Gingerbread soft keyboard, so it supports multitouch for the modifier keys.

This keyboard is especially useful if you use ConnectBot for SSH access. It provides working Tab/Ctrl/Esc keys, and the arrow keys are essential for devices such as the Xoom tablet or Nexus S that don't have a trackball or D-Pad.

The supported keyboard layouts include Armenian (Հայերեն), Arabic (العربية), British (en\_GB), Bulgarian (български език), Czech (Čeština), Danish (dansk), Dvorak English (language "en-DV"), English (QWERTY), Finnish (Suomi), French (Français, AZERTY), German (Deutsch, QWERTZ), Greek (ελληνικά), Hebrew (עברית), Hungarian (Magyar), Italian (Italiano), Lao (ພາສາລາວ), Norwegian (Norsk bokmål), Persian (فارسی), Portuguese (Português), Romanian (Română), Russian (Русский), Russian phonetic (Русский, ru-rPH), Serbian (Српски), Slovak (Slovenčina), Slovenian (Slovenščina)/Bosnian/Croatian/Latin Serbian, Spanish (Español, Español Latinoamérica), Swedish (Svenska), Tamil (தமிழ்), Thai (ไทย), Turkish (Türkçe), and Ukrainian (українська мова).

To install, get **[Hacker's Keyboard](https://f-droid.org/packages/org.pocketworkstation.pckeyboard/)** from the F-Droid Store, plus optional [dictionary packs](https://market.android.com/developer?pub=Klaus+Weidner).

## Additional resources ##

See the **ReleaseNotes** for changes in the Play Store released versions, and follow the project on **[Google+](https://plus.google.com/100683221734778417816)** for news and updates including pre-release versions.

Having problems? See the **UsersGuide** and **FrequentlyAskedQuestions**, and check the [issue tracker](https://github.com/klausw/hackerskeyboard/issues) for known bugs or filing new ones.

Comments, requests, or contributions? Join the [discussion group](http://groups.google.com/group/hackerskeyboard/).

Application developers: see KeyboardSupportInApplications if you want to enable the additional keys in your Android application, the same method also works for hardware USB or Bluetooth keyboards.

![http://hackerskeyboard.googlecode.com/files/hk-5row-en-s.png](http://hackerskeyboard.googlecode.com/files/hk-5row-en-s.png)