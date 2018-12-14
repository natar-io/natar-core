/*
 * Part of the PapARt project - https://project.inria.fr/papart/
 *
 * Copyright (C) 2017 RealityTech
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

import tech.lity.rea.nectar.camera.Camera;
import tech.lity.rea.nectar.camera.ImageUtils;
import java.nio.ByteBuffer;

import tech.lity.rea.nectar.utils.WithSize;
import java.util.ArrayList;
import org.bytedeco.javacpp.opencv_core.CvMat;
import org.bytedeco.javacpp.opencv_core.IplImage;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PMatrix3D;
import processing.core.PVector;
import redis.clients.jedis.Jedis;
import static tech.lity.rea.javacvprocessing.HomographyCreator.createHomography;
import tech.lity.rea.javacvprocessing.ProjectiveDeviceP;

/**
 *
 * @author Jeremy Laviole <laviole@rea.lity.tech>
 */
public class TrackedView implements WithSize {

    private PImage extractedPImage = null;
    private IplImage extractedIplImage = null;

    // private data
    private final PVector[] corner3DPos = new PVector[4];
//    private final PVector[] screenPixelCoordinates = new PVector[4];
//    private final PVector[] imagePixelCoordinates = new PVector[4];
    private final ArrayList<PVector> screenPixelCoordinates = new ArrayList<>(4);
    private final ArrayList<PVector> imagePixelCoordinates = new ArrayList<>(4);

    private boolean useManualConrers = false;
    private boolean useListofPairs = false;

    private PVector bottomLeftCorner = new PVector(0, 0), captureSizeMM = new PVector(100, 100);
    private PVector topLeftCorner = new PVector(0, 0);
    private boolean isYUp = true;

    private int imageWidthPx = 128, imageHeightPx = 128;

    // temporary variables
    private PMatrix3D pos = new PMatrix3D();
    private ProjectiveDeviceP pdp;
    private float heightOffset;

    public TrackedView() {
        this.useManualConrers = true;
    }

    public void setProjectiveDevice(ProjectiveDeviceP pdp) {
        this.pdp = pdp;
    }

    private boolean cornersSet = false;

    public void setCorners(PVector[] corners) {
        screenPixelCoordinates.clear();
        for (int i = 0; i < corners.length; i++) {
            screenPixelCoordinates.add(corners[i]);
        }
        cornersSet = true;
    }

    public void addObjectImagePair(PVector object, PVector image) {
        screenPixelCoordinates.add(object);
        imagePixelCoordinates.add(image);
    }

    public int getNbPairs() {
        return screenPixelCoordinates.size();
    }

    public void useListOfPairs(boolean use) {
        useListofPairs = use;
    }

    public void clearObjectImagePairs() {
        screenPixelCoordinates.clear();
        imagePixelCoordinates.clear();
    }

    public void init() {
        init(PApplet.RGB);
    }

    public void init(int frameType) {
        extractedPImage = new PImage(imageWidthPx, imageHeightPx, frameType);
        initiateImageCoordinates();
    }

    private void initiateImageCoordinates() {
        imagePixelCoordinates.clear();
        imagePixelCoordinates.add(new PVector(0, imageHeightPx));
        imagePixelCoordinates.add(new PVector(imageWidthPx, imageHeightPx));
        imagePixelCoordinates.add(new PVector(imageWidthPx, 0));
        imagePixelCoordinates.add(new PVector(0, 0));
    }

    public PVector pixelsToMM(PVector p) {
        return pixelsToMM(p.x, p.y);
    }

    public PVector pixelsToMM(float x, float y) {
        float outX = (x / imageWidthPx) * captureSizeMM.x + topLeftCorner.x;
        float outY = (y / imageHeightPx) * captureSizeMM.y + topLeftCorner.y;
        return new PVector(outX, outY);
    }

    public PImage getViewOf(Camera camera) {
        camera = Camera.checkActingCamera(camera);

        IplImage img = camera.getIplImage();
        if (!isExtractionReady(img)) {
            return null;
        }

        CvMat homography = computeHomography();

        boolean useRGB = camera.getPixelFormat() == Camera.PixelFormat.RGB;
        // Convert to the good type... 
        ImageUtils.remapImage(homography, img, extractedIplImage, extractedPImage, useRGB);
        return extractedPImage;
    }

    public PImage getViewOf(Camera camera, IplImage fakeImage) {
        camera = Camera.checkActingCamera(camera);

        IplImage img = fakeImage;
        if (!isExtractionReady(img)) {
            return null;
        }

        CvMat homography = computeHomography();

        boolean useRGB = camera.getPixelFormat() == Camera.PixelFormat.RGB;
        // Convert to the good type... 
        ImageUtils.remapImage(homography, img, extractedIplImage, extractedPImage, useRGB);
        return extractedPImage;
    }

    public IplImage getIplViewOf(Camera camera) {
        IplImage img = camera.getIplImage();
        if (!isExtractionReady(img)) {
            return null;
        }
        CvMat homography = computeHomography();
        ImageUtils.remapImageIpl(homography, img, extractedIplImage);

        return extractedIplImage;
    }

    public IplImage getIplViewOf(Camera camera, IplImage img) {
        if (!isExtractionReady(img)) {
            return null;
        }

        CvMat homography = computeHomography();
        ImageUtils.remapImageIpl(homography, img, extractedIplImage);
        return extractedIplImage;
    }

    private boolean isExtractionReady(IplImage img) {
        if (extractedPImage == null) {
            System.err.println("You should init the TrackedView before getting the view.");
            return false;
        }

        if (this.pdp == null) {
            System.err.println("You should set the Projective device in  TrackedView before getting the view.");
            return false;

        }
        if (img != null) {
            checkMemory(img);
        }
        return img != null && (useListofPairs && imagePixelCoordinates.size() >= 3
                || !useManualConrers
                || (useManualConrers && cornersSet));
    }

    /**
     * Advanced use only.
     *
     * @return
     */
    public CvMat computeHomography() {
        if (!this.useListofPairs) {
            computeCorners();
        }
        CvMat homography = createHomography(screenPixelCoordinates, imagePixelCoordinates);
        return homography;
    }

    private void checkMemory(IplImage memory) {
        if (extractedIplImage == null) {
            extractedIplImage = ImageUtils.createNewSizeImageFrom(memory, imageWidthPx, imageHeightPx);

            if (extractedIplImage == null) {
                System.err.println("Impossible to create a View! " + this + " " + extractedPImage);
            }
        }
    }

    public void setMainPosition(PMatrix3D pos) {
        this.pos = pos;
    }

    public void updateMainPosition(PMatrix3D pos) {
        this.pos.set(pos);
    }

    /**
     * Method called before any position update
     */
    public void updateMainPosition() {
    }

    private void computeCorners() {

        PMatrix3D pos = null;

        if (useManualConrers) {
            return;
        }

        updateMainPosition();

        if (pos == null) {
            throw new RuntimeException("ERROR in TrackedView, report this.");
        }

        PMatrix3D tmp = new PMatrix3D();

        tmp.apply(pos);

        for (int i = 0; i < 4; i++) {
            corner3DPos[i] = new PVector();
        }
        if (isYUp) {

            // bottom left
            tmp.translate(topLeftCorner.x, topLeftCorner.y);
            corner3DPos[0].x = tmp.m03;
            corner3DPos[0].y = tmp.m13;
            corner3DPos[0].z = tmp.m23;

            // bottom right
            tmp.translate(captureSizeMM.x, 0);
            corner3DPos[1].x = tmp.m03;
            corner3DPos[1].y = tmp.m13;
            corner3DPos[1].z = tmp.m23;

            // top right
            tmp.translate(0, -captureSizeMM.y, 0);
            corner3DPos[2].x = tmp.m03;
            corner3DPos[2].y = tmp.m13;
            corner3DPos[2].z = tmp.m23;

            // top left
            tmp.translate(-captureSizeMM.x, 0, 0);
            corner3DPos[3].x = tmp.m03;
            corner3DPos[3].y = tmp.m13;
            corner3DPos[3].z = tmp.m23;
        } else {
            // TODO: use BottowLeftCorner here ?!! 
            // top left
            tmp.translate(topLeftCorner.x, heightOffset - topLeftCorner.y);
            corner3DPos[3].x = tmp.m03;
            corner3DPos[3].y = tmp.m13;
            corner3DPos[3].z = tmp.m23;

            // top right
            tmp.translate(captureSizeMM.x, 0);
            corner3DPos[2].x = tmp.m03;
            corner3DPos[2].y = tmp.m13;
            corner3DPos[2].z = tmp.m23;

            // bottom right
            tmp.translate(0, -captureSizeMM.y, 0);
            corner3DPos[1].x = tmp.m03;
            corner3DPos[1].y = tmp.m13;
            corner3DPos[1].z = tmp.m23;

            // bottom left
            tmp.translate(-captureSizeMM.x, 0, 0);
            corner3DPos[0].x = tmp.m03;
            corner3DPos[0].y = tmp.m13;
            corner3DPos[0].z = tmp.m23;
        }

        screenPixelCoordinates.clear();
        for (int i = 0; i < 4; i++) {
            screenPixelCoordinates.add(pdp.worldToPixelUnconstrained(corner3DPos[i]));
//            screenPixelCoordinates.add(camera.pdp.worldToPixel(corner3DPos[i], true));
        }
        cornersSet = true;
    }

    public PVector getBottomLeftCorner() {
        return bottomLeftCorner.get();
    }

    public PVector getTopLeftCorner() {
        return topLeftCorner.get();
    }

    /**
     * Use either TopLeftCorner OR BottomLeftCorner. Calling one will discard
     * the other.
     *
     * @param bottomLeftCorner
     */
    public void setBottomLeftCorner(PVector bottomLeftCorner) {
        this.bottomLeftCorner.set(bottomLeftCorner);
        this.isYUp = true;
        forceYOrientation(true, 0);
    }

    public void forceYOrientation(boolean up, float height) {
        this.isYUp = up;
        this.heightOffset = height;
    }

    /**
     * Use either TopLeftCorner OR BottomLeftCorner.Calling one will discard the
     * other.
     *
     * @param topLeftCorner
     * @param height height offset (ex: height of a PaperScreen).
     */
    public void setTopLeftCorner(PVector topLeftCorner, float height) {
        this.topLeftCorner.set(topLeftCorner);
        forceYOrientation(false, height);
    }

    public void setScale(float scale) {
        this.imageWidthPx = (int) (captureSizeMM.x * scale);
        this.imageHeightPx = (int) (captureSizeMM.y * scale);
    }

    public PVector getCaptureSizeMM() {
        return captureSizeMM.copy();
    }

    public void setCaptureSizeMM(PVector captureSizeMM) {
        this.captureSizeMM.set(captureSizeMM);
    }

    public int getImageWidthPx() {
        return imageWidthPx;
    }

    public TrackedView setImageWidthPx(int imageWidthPx) {
        this.imageWidthPx = imageWidthPx;
        return this;
    }

    public int getImageHeightPx() {
        return imageHeightPx;
    }

    /**
     * Get Pixel width.
     *
     * @return
     */
    public int getWidth() {
        return imageWidthPx;
    }

    /**
     * Get pixel height.
     *
     * @return
     */
    public int getHeight() {
        return imageHeightPx;
    }

    /**
     * Get pixel size.
     *
     * @return
     */
    public int getSize() {
        return getWidth() * getHeight();
    }

    public TrackedView setImageHeightPx(int imageHeightPx) {
        this.imageHeightPx = imageHeightPx;
        return this;
    }

}
