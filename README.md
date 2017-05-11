# Image Cropper

JavaFX Scala GUI for cropping desktop wallpaper.

I got tired of my traditional plain black desktop and had quite a few very large images that weren't desktop-shaped. I wanted a nice way to crop the mostest interestingest bits to size and faffing about in image editors isn't much fun. It was also a good excuse to play with a desktop GUI toolkit and I always found Swing to be a bit of a horror. JavaFX seems pretty friendly.

Double click the middle somewhere to load an image. Drag with the mouse and zoom in and out with the wheel. Use LEFT and RIGHT to alter compression quality and RMB or ENTER to crop, scale and save the current view to a new image. Old image is unchanged. Press DELETE to delete. New image is named from the old image suffixed with the JPEG compression quality to avoid collision because all those confirmation dialogs would be tiresome. Sized at 2/3 of the display to give the correct aspect. Checkbox to enable/disable progressive, red moany text in the bottom corner if you're scaling up because making up pixels is bad for you or green happy text if you're setting pixels free because you have too many.

TODO: pin the point under the mouse when zooming so the view doesn't reset as you zoom in and out.

Looks like this ...

![Snapshot](https://github.com/worthlesscog/image-cropper/blob/master/res/screenshot.jpg)

sbt assembly to build an executable JAR.
