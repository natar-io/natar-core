/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tech.lity.rea.nectar.camera;

import org.bytedeco.javacpp.opencv_core.IplImage;

/**
 *
 * @author jiii
 */
public interface LockedUpdater {

    public void lock();

    public void unlock();

    public void update();
    public void updateColors(IplImage colorImage);
}
