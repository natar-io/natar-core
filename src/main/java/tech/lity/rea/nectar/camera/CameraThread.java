/*
 * Part of the PapARt project - https://project.inria.fr/papart/
 *
 * Copyright (C) 2014-2016 Inria
 * Copyright (C) 2011-2013 Bordeaux University
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, version 2.1.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; If not, see
 * <http://www.gnu.org/licenses/>.
 */
package tech.lity.rea.nectar.camera;

/**
 *
 * @author jeremylaviole
 */
public class CameraThread extends Thread {

    private final Camera camera;
    Camera cameraForMarkerboard;
    private boolean compute;

    public boolean stop;

    public CameraThread(Camera camera) {
        this.camera = camera;
        stop = false;
        cameraForMarkerboard = camera;
    }

    @Override
    public void run() {
        while (!stop) {
            checkSubCamera();
            camera.grab();
            // If there is no camera for tracking...
            if (cameraForMarkerboard == null || !compute) {
                continue;
            }
        }
    }

    private void checkSubCamera() {
        if (!(camera instanceof CameraRGBIRDepth)) {
            return;
        }
    }


    public void stopThread() {
        stop = true;
    }
}
