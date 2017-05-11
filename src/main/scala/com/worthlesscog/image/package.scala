package com.worthlesscog

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.{ File, FileOutputStream }

import javafx.event.{ Event, EventHandler }
import javafx.geometry.{ HPos, Insets, Orientation, Rectangle2D }
import javafx.scene.SnapshotParameters
import javafx.scene.control.{ CheckBox, Control, Slider, Tooltip }
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.layout.{ ColumnConstraints, GridPane }
import javafx.stage.FileChooser.ExtensionFilter
import javax.imageio.{ IIOImage, ImageIO, ImageWriteParam, ImageWriter }

package object image {

    implicit class Pipe[A](a: A) {
        def |>[B](f: A => B): B = f(a)
    }

    type Dimensioned = {
        def getHeight(): Double
        def getWidth(): Double
    }

    // ----------

    def alert(t: AlertType, title: String, header: String, content: String) = {
        val a = new Alert(t)
        a.setTitle(title)
        a.setHeaderText(header)
        a.setContentText(content)
        a
    }

    def checkBox(tip: String) =
        new CheckBox |> withTooltip(tip)

    def col(percentWidth: Double) = {
        val c = new ColumnConstraints
        c.setHalignment(HPos.CENTER)
        c.setPercentWidth(percentWidth)
        c
    }

    def confirmation(title: String, header: String, content: String) =
        alert(AlertType.CONFIRMATION, title, header, content)

    def insetGridPane = {
        val g = new GridPane
        g.setPadding(standardInsets)
        g
    }

    def handlerFor[T <: Event](a: (T) => Unit) =
        new EventHandler[T] {
            def handle(e: T) = a(e)
        }

    def horizontalSlider(tip: String, bottom: Double, top: Double, v: Double, increment: Double) = {
        val s = new Slider(bottom, top, v) |> withTooltip(tip)
        s.setBlockIncrement(increment)
        s.setMajorTickUnit(5)
        s.setMinorTickCount(4)
        s.setOrientation(Orientation.HORIZONTAL)
        s.setSnapToTicks(true)
        s.setShowTickLabels(true)
        s.setShowTickMarks(true)
        s
    }

    def imageFilter =
        new ExtensionFilter("Image files", "*.BMP", "*.JPEG", "*.JPG", "*.PNG", "*.bmp", "*.jpeg", "*.jpg", "*.png")

    def plainSnapshot =
        new SnapshotParameters

    def rect(tlx: Double, tly: Double, w: Double, h: Double) =
        new Rectangle2D(tlx, tly, w, h)

    def selectedCheckBox(tip: String) = {
        val c = checkBox(tip)
        c.setSelected(true)
        c
    }

    def standardInsets =
        new Insets(5, 5, 5, 5)

    def tooltip(s: String) =
        new Tooltip(s)

    def warning(title: String, header: String, content: String) =
        alert(AlertType.WARNING, title, header, content)

    def withTooltip[T <: Control](tip: String)(c: T) = {
        tooltip(tip) |> c.setTooltip
        c
    }

    // ----------

    def fos(f: File) =
        new FileOutputStream(f)

    def saveJpg(f: File, progressive: Boolean, q: Float)(i: BufferedImage) = {
        def compressionParams(w: ImageWriter) = {
            val p = w.getDefaultWriteParam
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
            p.setCompressionQuality(q / 100)
            if (progressive)
                p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT)
            p
        }
        val w = ImageIO.getImageWritersByFormatName("jpg").next
        f |> fos |> ImageIO.createImageOutputStream |> w.setOutput
        w.write(null, new IIOImage(i, null, null), compressionParams(w))
        w.dispose
    }

    def scale(i: BufferedImage, w: Int, h: Int, hints: Int) = {
        val b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = b.createGraphics
        g.drawImage(i.getScaledInstance(w, h, hints), 0, 0, null)
        g.dispose
        b
    }

    def scaleSmooth(w: Int, h: Int)(i: BufferedImage) =
        scale(i, w, h, Image.SCALE_SMOOTH)

    def stub(f: File) =
        f.getName.substring(0, f.getName.lastIndexOf('.'))

}
