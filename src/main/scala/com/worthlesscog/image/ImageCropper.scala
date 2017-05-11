package com.worthlesscog.image

import java.io.File

import scala.language.reflectiveCalls

import javafx.application.Application
import javafx.beans.property.SimpleObjectProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.control.{ ButtonType, Label }
import javafx.scene.image.{ Image, ImageView }
import javafx.scene.input.{ KeyCode, KeyEvent, MouseButton, MouseEvent, ScrollEvent }
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.stage.{ FileChooser, Screen, Stage }

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
    val info = new Label("No Image")
    val progressive = selectedCheckBox("Progressive JPEG")
    val quality = horizontalSlider("JPEG Quality", 50, 99, 90, 1)
    val status = new Label("-")
    val target = new Label(dimensions(bounds))
    val view = new ImageView

    def start(stage: Stage) {
        view.setPreserveRatio(true)
        view.setFitWidth((bounds.getWidth * 2) / 3)
        view.setFitHeight((bounds.getHeight * 2) / 3)
        handlerFor(dragMouse) |> view.setOnMouseDragged
        handlerFor(dragStart) |> view.setOnMousePressed
        handlerFor(scrolling) |> view.setOnScroll

        val grid = insetGridPane
        grid.add(info, 0, 0)
        grid.add(quality, 1, 0)
        grid.add(progressive, 2, 0)
        grid.add(target, 3, 0)
        grid.add(status, 4, 0)
        grid.getColumnConstraints.addAll(col(50), col(20), col(5), col(5), col(20))

        val pane = new BorderPane
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

    def confirmDelete(f: File) =
        confirmation("Confirm File Delete", null, s"OK to delete $f ?").showAndWait.get == ButtonType.OK

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
                if (imageLoaded && confirmDelete(file.get)) {
                    file.get.delete
                    updateLabel(status, Color.BLACK, "Deleted")
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
                f.toString + ", " + dimensions(i) |> info.setText
                view.setImage(i)
                initialZoom(i, view.getFitWidth, view.getFitHeight) |> view.setViewport
                updateStatus
            }
        }

    def imageLoaded =
        (view.getImage != null) && !view.getImage.isError

    def imageSave(stage: Stage) = {
        val f = new File(file.get.getParentFile, stub(file.get) + "." + quality.getValue.toInt + ".jpg")
        Option(f) |> selectFile(stage, SAVE) foreach { f =>
            val s = view.snapshot(plainSnapshot, null)
            val (w, h) = targetSize
            SwingFXUtils.fromFXImage(s, null) |> scaleSmooth(w, h) |> saveJpg(f, progressive.isSelected, quality.getValue.toFloat)
            updateLabel(status, Color.BLACK, "Saved")
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

    def scrolling(e: ScrollEvent) = {
        val i = view.getImage
        val (vW, vH) = (view.getViewport.getWidth, view.getViewport.getHeight)

        val max = (i.getWidth / vW) min (i.getHeight / vH)
        val min = (i.getWidth / vW * 0.25) min (i.getHeight / vH * 0.25)
        val base = if (e.isControlDown) 1.0001 else 1.001
        val z = math.pow(base, -e.getDeltaY) min max max min
        rect(0, 0, z * vW, z * vH) |> view.setViewport

        updateStatus
    }

    def selectFile(s: Stage, load: Boolean)(file: Option[File]) = {
        val fc = new FileChooser
        fc.getExtensionFilters.add(imageFilter)
        file foreach { f =>
            fc.setInitialDirectory(f.getParentFile)
            fc.setInitialFileName(f.getName)
        }
        val f = if (load) fc.showOpenDialog(s) else fc.showSaveDialog(s)
        f match {
            case null =>
                None

            case _ =>
                Some(f)
        }
    }

    def targetSize =
        (bounds.getWidth.toInt, bounds.getHeight.toInt)

    def updateStatus = {
        val vp = view.getViewport
        val (vW, vH) = ((vp.getWidth + 0.5).toInt, (vp.getHeight + 0.5).toInt)
        val (tW, tH) = targetSize
        val (colour, dir) = if (vW < tW || vH < tH) (Color.RED, "up") else (Color.GREEN, "down")
        updateLabel(status, colour, dimensions(vp) + ", scale " + dir)
    }

    def updateLabel(l: Label, c: Color, s: String) = {
        l.setTextFill(c)
        l.setText(s)
    }

}

object ImageCropper {

    def main(args: Array[String]) =
        Application.launch(classOf[ImageCropper], args: _*)

}
