# Pinstream
Minimal audio stream player and manager for Android

Allows you to maintain a 'bookmarks' list of audio stream URLs, 
and can be controlled from an in-app 'Now Playing' display that
shows an audio-responsive 'wave' visualiser.

While playing, the app will display a sticky notification allowing
you to play/pause and stop the stream. It should also respond to
input from external devices such as Android wear, though this
functionality is not yet tested.

To-do:
- Parse the stream for metadata for display in-app and in the 
notification, along with artwork retrieval
- Controls to skip backwards and forwards between the streams 
you have saved
- Ability to drag the list items into a preferred order
- Swipe an item to the left to display delete/edit options
rather than display dialog
- Fix bug where app crashes if stream is stopped from the
notification while the phone is unlocked
