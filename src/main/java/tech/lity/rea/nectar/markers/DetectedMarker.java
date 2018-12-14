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
package tech.lity.rea.nectar.markers;

import tech.lity.rea.javacvprocessing.ProjectiveDeviceP;
import java.util.ArrayList;
import java.util.Arrays;
import org.bytedeco.javacv.Marker;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
import processing.core.PVector;

public class DetectedMarker implements Cloneable {

    public int id;
    public double[] corners;
    public double confidence;

    public DetectedMarker(int id, double[] corners, double confidence) {
        this.id = id;
        this.corners = corners;
        this.confidence = confidence;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Marker id: " + id + " corners: "
                + corners[0] + " "
                + corners[1] + " "
                + corners[2] + " "
                + corners[3] + " "
                + corners[4] + " "
                + corners[5] + " "
                + corners[6] + " "
                + corners[7];
    }

    public Marker copyAsMarker() {
        return new org.bytedeco.javacv.Marker(id, corners, confidence);
    }

    public void drawSelf(PGraphics g, int size) {
        for (int i = 0; i < 8; i += 2) {
            g.ellipse((float) corners[i], (float) corners[i + 1], size, size);
        }
    }

    public PVector[] getCorners() {
        PVector[] out = new PVector[4];
        out[0] = new PVector((float) corners[0], (float) corners[1]);
        out[1] = new PVector((float) corners[2], (float) corners[3]);
        out[2] = new PVector((float) corners[4], (float) corners[5]);
        out[3] = new PVector((float) corners[6], (float) corners[7]);
        return out;
    }

    public DetectedMarker(int id, double... corners) {
        this(id, corners, 1.0);
    }

    @Override
    public DetectedMarker clone() {
        return new DetectedMarker(id, corners.clone(), confidence);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DetectedMarker) {
            DetectedMarker m = (DetectedMarker) o;
            return m.id == id && Arrays.equals(m.corners, corners);
        }
        return false;
    }

    public double[] getCenter() {
        double x = 0, y = 0;
        if (true) {
// the centroid is not what we want as it does not remain at
// the same physical point under projective transformations..
// But it has the advantage of averaging noise better, and does
// give better results
            for (int i = 0; i < 4; i++) {
                x += corners[2 * i];
                y += corners[2 * i + 1];
            }
            x /= 4;
            y /= 4;
        } else {
            double x1 = corners[0];
            double y1 = corners[1];
            double x2 = corners[4];
            double y2 = corners[5];
            double x3 = corners[2];
            double y3 = corners[3];
            double x4 = corners[6];
            double y4 = corners[7];

            double u = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3))
                    / ((y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1));
            x = x1 + u * (x2 - x1);
            y = y1 + u * (y2 - y1);
        }
        return new double[]{x, y};
    }

    /**
     * Find the 3D position of detected markers. It uses the solvePnP function
     * of OpenCV.
     *
     * @param detectedMarkers Array of markers found. (Image)
     * @param markersFromSVG Model.
     * @param camera its calibration is used.
     * @return
     */
    public static PMatrix3D compute3DPos(DetectedMarker[] detectedMarkers, MarkerList markersFromSVG,
            tech.lity.rea.nectar.camera.Camera camera) {
        // We create a pair model ( markersFromSVG) -> observation (markers) 

//         markersFromSVG
        ArrayList<PVector> objectPoints = new ArrayList<PVector>();
        ArrayList<PVector> imagePoints = new ArrayList<PVector>();
        int k = 0;

        for (DetectedMarker detected : detectedMarkers) {
            if (markersFromSVG.containsKey(detected.id)) {

//                System.out.println("Detected marker: " + detected.id + " confidence " + detected.confidence);
                if (detected.confidence < 1.0) {
                    continue;
                }

                // Center instead ? 
//                PVector object = markersFromSVG.get(detected.id).getCenter();
////                PVector image = detected.getCenter();
//                double[] im = detected.getCenter();
//                PVector image = new PVector((float) im[0], (float) im[1]);
//                objectPoints.add(object);
//                imagePoints.add(image);
                ////// Corners 
                PVector[] object = markersFromSVG.get(detected.id).getCorners();
                PVector[] image = detected.getCorners();
                for (int i = 0; i < 4; i++) {
//                    System.out.println("Model " + object[i] + " image " + image[i]);
                    objectPoints.add(object[i]);
                    imagePoints.add(image[i]);
                }
                k++;
            }
        }
//        if (k < 4) {
        if (k < 1) { // TODO: Better error Handling
            return new PMatrix3D();
        }

        PVector[] objectArray = new PVector[k];
        PVector[] imageArray = new PVector[k];
        objectArray = objectPoints.toArray(objectArray);
        imageArray = imagePoints.toArray(imageArray);

        ProjectiveDeviceP pdp = camera.getProjectiveDevice();

//        System.out.println("Pose estimation: " + pdp.toString());
//        System.out.println("Object/image: ");
//        for(int i = 0; i < k; i++)
//        {
//            System.out.println(objectArray[i] + " " + imageArray[i]);
//        }
        return pdp.estimateOrientation(objectArray, imageArray);
//        return pdp.estimateOrientationRansac(objectArray, imageArray);
    }

}
