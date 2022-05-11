/**
 * @author blog.unclezs.com
 * @date 2021/04/01 11:36
 */
module com.unclezs.jfx.launcher {
  requires static lombok;
  requires com.google.gson;
  requires javafx.fxml;
  requires javafx.controls;
  requires javafx.graphics;
  requires java.logging;

  opens com.unclezs.jfx.launcher to com.google.gson;
  exports com.unclezs.jfx.launcher;
}
