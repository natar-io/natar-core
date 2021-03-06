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
package tech.lity.rea.nectar.tracking;

import tech.lity.rea.nectar.camera.Camera;
import org.bytedeco.javacpp.ARToolKitPlus;
import org.bytedeco.javacpp.opencv_core.IplImage;
import java.util.ArrayList;
import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PVector;
import tech.lity.rea.nectar.camera.TrackedObject;

/**
 *
 * @author jeremylaviole
 */
public abstract class MarkerBoard implements TrackedObject {

    protected String fileName;
    protected float width;
    protected float height;
    protected ArrayList<Camera> cameras;
    protected ArrayList<Boolean> drawingMode;
    protected ArrayList<Float> minDistanceDrawingMode;
    protected ArrayList<PMatrix3D> transfos = new ArrayList<PMatrix3D>();
    protected ArrayList trackers;
    protected ArrayList<OneEuroFilter[]> filters;
    protected ArrayList<PVector> lastPos;
    protected ArrayList<Float> lastDistance;
    protected ArrayList<Integer> nextTimeEvent;
    protected ArrayList<Integer> updateStatus;
    protected PApplet applet;

    protected MarkerType type = null;

    protected static final int updateTime = 1000; // 1 sec
    static public final int NORMAL = 1;
    static public final int BLOCK_UPDATE = 2;
    static public final int FORCE_UPDATE = 3;
    static public final PMatrix3D INVALID_LOCATION = new PMatrix3D();

    public enum MarkerType {

        ARTOOLKITPLUS, JAVACV_FINDER, SVG, SVG_NECTAR, INVALID
    }

    public String getName() {
        return this.fileName;
    }

    public MarkerType getMarkerType() {
        return type;
    }

    protected MarkerBoard() {
        this.fileName = "Invalid MarkerBoard";
    }

    public MarkerBoard(float width, float height) {
        this.width = width;
        this.height = height;
        cameras = new ArrayList<Camera>();
        filters = new ArrayList<OneEuroFilter[]>();
        drawingMode = new ArrayList<Boolean>();
        minDistanceDrawingMode = new ArrayList<Float>();
        lastPos = new ArrayList<PVector>();
        lastDistance = new ArrayList<Float>();
        nextTimeEvent = new ArrayList<Integer>();
        updateStatus = new ArrayList<Integer>();
    }

    public MarkerBoard(String fileName, float width, float height) {
        this(width, height);
        this.fileName = fileName;
    }

    protected abstract void addTrackerImpl(Camera camera);

    @Override
    public void addTracker(PApplet applet, Camera camera) {
//    public void addTracker(PApplet applet, Camera camera, ARToolKitPlus.TrackerMultiMarker tracker, float[] transfo) {
        this.applet = applet;

        this.cameras.add(camera);
        this.drawingMode.add(false);
        this.lastPos.add(new PVector());
        this.lastDistance.add(0f);
        this.minDistanceDrawingMode.add(2f);
        this.nextTimeEvent.add(0);
        this.updateStatus.add(NORMAL);
        OneEuroFilter[] filter = null;
        this.filters.add(filter);
        addTrackerImpl(camera);
    }

    public int getId(Camera camera) {
        return cameras.indexOf(Camera.checkActingCamera(camera));
    }

    public void setFiltering(Camera camera, double freq, double minCutOff) {
        int id = getId(camera);
        OneEuroFilter[] filter = createFilter(freq, minCutOff);
        filters.set(id, filter);
    }

    public void removeFiltering(Camera camera) {
        int id = getId(camera);
        filters.set(id, null);
    }

    public void setDrawingMode(Camera camera, boolean dm) {
        setDrawingMode(camera, dm, 2);
    }

    public void setDrawingMode(Camera camera, boolean dm, float dist) {
        int id = getId(camera);

        drawingMode.set(id, dm);
        minDistanceDrawingMode.set(id, dist);
    }

    public void setFakeLocation(Camera camera, PMatrix3D location) {
        int id = getId(camera);
        PMatrix3D transfo = (PMatrix3D) transfos.get(id);
        transfo.set(location);
    }

    protected int subscribersAmount = 0;

    /**
     * Tell this markerboard that something is getting the position.
     */
    public void subscribe() {
        subscribersAmount++;
//        System.out.println("DEBUG: subscription: " + subscribersAmount);
    }

    /**
     * Tell this markerboard that its position is not used anymore by one
     * object.
     */
    public void unsubscribe() {
        subscribersAmount--;
//        System.out.println("DEBUG: UNsubscription: " + subscribersAmount);
    }

    public OneEuroFilter[] NO_FILTERS = new OneEuroFilter[0];

    private OneEuroFilter[] createFilter(double freq, double minCutOff) {
        OneEuroFilter[] f = new OneEuroFilter[12];
        try {
            for (int i = 0; i < 12; i++) {
                f[i] = new OneEuroFilter(freq);
                f[i].setFrequency(freq);
                f[i].setMinCutoff(minCutOff);
            }
        } catch (Exception e) {
            System.out.println("Filter init error" + e);
        }

        return f;
    }

//    public MultiTracker getTracker() {
//        return this.tracker;
//    }
    public void forceUpdate(Camera camera, int time) {
        int id = getId(camera);
        nextTimeEvent.set(id, applet.millis() + time);
        updateStatus.set(id, FORCE_UPDATE);
    }

    public void blockUpdate(Camera camera, int time) {
        int id = getId(camera);
        nextTimeEvent.set(id, applet.millis() + time);
        updateStatus.set(id, BLOCK_UPDATE);
    }

    public boolean isMoving(Camera camera) {
        int id = getId(camera);
//        nextTimeEvent.set(id, applet.millis() + time);
        int mode = updateStatus.get(id);

        if (mode == BLOCK_UPDATE) {
            return false;
        }
        if (mode == FORCE_UPDATE || mode == NORMAL) {
            return true;
        }

        return true;
    }

    public float lastMovementDistance(Camera camera) {
        int id = getId(camera);
        return lastDistance.get(id);
    }

    public boolean isTrackedBy(Camera camera) {
        if (this == MarkerBoardInvalid.board) {
//            System.out.println("ERROR: cannot get the position of an invalid board.");
        }
        return cameras.contains(camera);
    }

    public PVector getPositionVector(int id) {
        PMatrix3D transfo = (PMatrix3D) transfos.get(id);
        return new PVector(transfo.m03, transfo.m13, transfo.m23);
    }

    public synchronized void updateLocation(Camera camera, IplImage img, Object globalTracking) {
        int id = getId(camera);
        if (id == -1) {
            throw new RuntimeException("The board " + this.fileName + " is"
                    + " not registered with the camera you asked");
        }

        int currentTime = 0;
        if (applet == null) {
            currentTime = (int) System.currentTimeMillis();
        } else {
            currentTime = applet.millis();
        }
        int endTime = nextTimeEvent.get(id);
        int mode = updateStatus.get(id);

        // If the update is still blocked
        if (mode == BLOCK_UPDATE && currentTime < endTime) {
            return;
        }
        updatePositionImpl(id, currentTime, endTime, mode, camera, img, globalTracking);
    }

    protected abstract void updatePositionImpl(int id, int currentTime, int endTime, int mode, Camera camera, IplImage img, Object globalTracking);

    public PMatrix3D getPosition(Camera camera) {
        return transfos.get(getId(camera));
    }

    @Deprecated
    public PMatrix3D getTransfoMat(Camera camera) {
        return transfos.get(getId(camera));
    }

    public PMatrix3D getTransfoRelativeTo(Camera camera, MarkerBoard board2) {

        PMatrix3D tr1 = getPosition(camera);
        PMatrix3D tr2 = board2.getPosition(camera);

        tr2.apply(tr1);
        return tr2;
    }

    private Object getTracking(Camera camera) {
        return trackers.get(getId(camera));
    }

    public boolean useJavaCVFinder() {
        return this.type == MarkerType.JAVACV_FINDER;
    }

    public boolean useMarkers() {
        return this.type == MarkerType.ARTOOLKITPLUS ||
                this.type == MarkerType.SVG_NECTAR ||
                this.type == MarkerType.SVG;
    }

    public boolean useGrayImages() {
        return this.type == MarkerType.ARTOOLKITPLUS ||  this.type == MarkerType.SVG_NECTAR || this.type == MarkerType.SVG;
    }

    public boolean useCustomARToolkitBoard() {
        return this.type == MarkerType.SVG ||  this.type == MarkerType.SVG_NECTAR;
    }

    public String getFileName() {
        return fileName;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public String toString() {
        return "MarkerBoard " + getFileName();
    }
}
