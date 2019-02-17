package com.worthlesscog.image

import java.io.File

import javafx.application.Application
import javafx.beans.property.SimpleObjectProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.control.{ButtonType, Label}
import javafx.scene.image.Image
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent, ScrollEvent}
import javafx.scene.paint.Color
import javafx.stage.{Screen, Stage}

case class XY(x: Double, y: Double) {
    def -(xy: XY) = XY(x - xy.x, y - xy.y)
}

object XY {
    def apply(e: MouseEvent): XY = XY(e.getX, e.getY)
}

class ImageCropper extends Application {

    val LOAD = true
    val SAVE = false

    val file = new SimpleObjectProperty[File]
    val xy = new SimpleObjectProperty[XY]

    val bounds = Screen.getPrimary.getBounds
    val filler = emptyLabel
    val progressive = selectedCheckBox("Progressive")
    val quality = horizontalSlider("Quality", 50, 99, 90, 1)
    val scaling = label("View")("-")
    val source = label("Source")("-")
    val target = dimensions(bounds) |> label("Target")
    val view = trueAspectView

    def start(stage: Stage) {
        view.setFitWidth((bounds.getWidth * 2) / 3)
        view.setFitHeight((bounds.getHeight * 2) / 3)
        handlerFor(dragMouse) |> view.setOnMouseDragged
        handlerFor(dragStart) |> view.setOnMousePressed
        handlerFor(scrolling) |> view.setOnScroll

        val grid = insetGridPane
        grid.addRow(0, target, source, scaling, filler, quality, progressive)
        grid.getColumnConstraints.addAll(colPercent(5), colPercent(5), colPercent(5), colGrow, colPercent(15), colPercent(5))

        val pane = borderPane
        pane.setCenter(view)
        pane.setBottom(grid)
        handlerFor(keyboard(stage)) |> pane.setOnKeyPressed
        handlerFor(imageIO(stage)) |> pane.setOnMouseClicked

        stage.setTitle("Image Cropper")
        stage.setResizable(false)
        new Scene(pane) |> stage.setScene
        stage.show
    }

    def alert(description: String, cause: String) =
        warning("Oops!", description, cause).showAndWait

    def confirmDelete(stage: Stage, f: File) = {
        val c = confirmation("Confirm File Delete", null, "OK to delete " + f.getName + " ?")
        c.initOwner(stage)
        c.showAndWait.get == ButtonType.OK
    }

    def dimensions[T <: Dimensioned](d: T) =
        (d.getWidth + 0.5).toInt + " x " + (d.getHeight + 0.5).toInt

    def doubleLeftClick(e: MouseEvent) =
        e.getClickCount == 2 && e.getButton == MouseButton.PRIMARY

    def dragMouse(e: MouseEvent) =
        Option(view.getImage) foreach { i =>
            val at = XY(e)
            val δ = xy.get - at
            xy.set(at)
            val vp = view.getViewport
            val nX = (vp.getMinX + δ.x) max 0 min (i.getWidth - vp.getWidth)
            val nY = (vp.getMinY + δ.y) max 0 min (i.getHeight - vp.getHeight)
            rect(nX, nY, vp.getWidth, vp.getHeight) |> view.setViewport
        }

    def dragStart(e: MouseEvent) =
        XY(e) |> xy.set

    def keyboard(stage: Stage)(e: KeyEvent) =
        e.getCode match {
            case KeyCode.DELETE =>
                if (imageLoaded && confirmDelete(stage, file.get)) {
                    file.get.delete
                    updateLabel(source, Color.BLACK)("Deleted")
                }

            case KeyCode.ENTER =>
                if (imageLoaded)
                    imageSave(stage)

            case KeyCode.LEFT =>
                quality.decrement

            case KeyCode.RIGHT =>
                quality.increment

            case _ =>
        }

    def imageIO(stage: Stage)(e: MouseEvent) =
        if (e |> doubleLeftClick)
            imageLoad(stage)
        else if (rightClick(e) && imageLoaded)
            imageSave(stage)

    def imageLoad(stage: Stage) =
        Option(file.get) |> selectFile(stage, LOAD) foreach { f =>
            file.set(f)
            val i = new Image("file:" + f.getPath)
            if (i.isError)
                alert("Can't read " + f.getPath, i.getException.toString)
            else {
                stage.setTitle(f.getName)
                updateDimensions(source, i)
                view.setImage(i)
                initialZoom(i, view.getFitWidth, view.getFitHeight) |> view.setViewport
                updateScaling
            }
        }

    def imageLoaded =
        (view.getImage != null) && !view.getImage.isError

    def imageSave(stage: Stage) = {
        val f = new File(file.get.getParentFile, stub(file.get) + "." + quality.getValue.toInt + ".jpg")
        Option(f) |> selectFile(stage, SAVE) foreach { f =>
            val s = view.snapshot(plainSnapshot, null)
            val (w, h) = targetSize
            backgroundTask(save(s, w, h, f, progressive.isSelected, quality.getValue.toFloat))
        }
    }

    def initialZoom(i: Image, w: Double, h: Double) =
        if (i.getWidth >= w && i.getHeight >= h)
            rect(0, 0, w, h)
        else {
            // XXX - find a non-crap way to do this
            var (x, y, δy) = (0.0, 0.0, h / w)
            while (x < i.getWidth && y < i.getHeight) {
                x += 1
                y += δy
            }
            rect(0, 0, x, y)
        }

    def rightClick(e: MouseEvent) =
        e.getClickCount == 1 && e.getButton == MouseButton.SECONDARY

    def save(i: Image, w: Int, h: Int, f: File, progressive: Boolean, quality: Float) =
        // XXX - safety this up in case it fails
        SwingFXUtils.fromFXImage(i, null) |> scaleSmooth(w, h) |> saveJpg(f, progressive, quality)

    def scrolling(e: ScrollEvent) = {
        val i = view.getImage
        val (vW, vH) = (view.getViewport.getWidth, view.getViewport.getHeight)

        val max = (i.getWidth / vW) min (i.getHeight / vH)
        val min = (i.getWidth / vW * 0.25) min (i.getHeight / vH * 0.25)
        val base = if (e.isControlDown) 1.0001 else 1.001
        val z = math.pow(base, -e.getDeltaY) min max max min
        rect(0, 0, z * vW, z * vH) |> view.setViewport

        updateScaling
    }

    def selectFile(s: Stage, load: Boolean)(file: Option[File]) = {
        val c = imageChooser
        file foreach { f =>
            if (f.getParentFile.exists) {
                c.setInitialDirectory(f.getParentFile)
                c.setInitialFileName(f.getName)
            }
        }
        val f = if (load) c.showOpenDialog(s) else c.showSaveDialog(s)
        f match {
            case null =>
                None

            case _ =>
                Some(f)
        }
    }

    def targetSize =
        (bounds.getWidth.toInt, bounds.getHeight.toInt)

    def updateDimensions(l: Label, d: Dimensioned) = {
        val (dW, dH) = ((d.getWidth + 0.5).toInt, (d.getHeight + 0.5).toInt)
        val (tW, tH) = targetSize
        val colour = if (dW < tW || dH < tH) Color.RED else Color.GREEN
        dimensions(d) |> updateLabel(l, colour)
    }

    def updateScaling =
        updateDimensions(scaling, view.getViewport)

    def updateLabel(l: Label, c: Color)(s: String) = {
        l.setTextFill(c)
        l.setText(s)
    }

}

object Launcher {

    def main(args: Array[String]) =
        Application.launch(classOf[ImageCropper], args: _*)

}
