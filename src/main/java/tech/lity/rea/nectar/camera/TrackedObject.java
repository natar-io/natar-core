package tech.lity.rea.nectar.camera;

import processing.core.PApplet;
import processing.core.PVector;
import tech.lity.rea.nectar.camera.Camera;

/**
 *
 */
public interface TrackedObject {
    
    public String getName();
    public void addTracker(PApplet applet, Camera camera);

}
