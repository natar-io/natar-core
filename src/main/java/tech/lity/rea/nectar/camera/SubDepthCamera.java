/*
 * Copyright (C) 2016  RealityTech. 
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package tech.lity.rea.nectar.camera;

import org.bytedeco.javacpp.opencv_core;

/**
 *
 * @author Jeremy Laviole
 */
public class SubDepthCamera extends SubCamera{
    

    public SubDepthCamera(CameraRGBIRDepth mainCamera) {
        super(mainCamera);
    }
    
    public SubDepthCamera(CameraRGBIRDepth mainCamera, Type type) {
        super(mainCamera, type);
    }
 

    public void newTouchImageWithColor(opencv_core.IplImage colorImage) {
//            touchInput.lock();
//            touchInput.update();
//            touchInput.getTouch2DColors(colorImage);
//            touchInput.unlock();
    }
    public void newTouchImage() {
//            touchInput.lock();
//            touchInput.update();
//            touchInput.unlock();
    }
}
