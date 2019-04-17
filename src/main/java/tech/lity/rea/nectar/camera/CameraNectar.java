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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bytedeco.javacpp.opencv_core;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import processing.core.PImage;
import processing.core.PMatrix3D;
import processing.data.JSONArray;
import processing.data.JSONObject;
import redis.clients.jedis.BinaryClient;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.BitOP;
import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.ZParams;
import redis.clients.jedis.params.sortedset.ZAddParams;
import redis.clients.jedis.params.sortedset.ZIncrByParams;
import tech.lity.rea.javacvprocessing.ProjectiveDeviceP;
import tech.lity.rea.nectar.markers.DetectedMarker;
import tech.lity.rea.nectar.tracking.MarkerBoard;

/**
 *
 * @author Jeremy Laviole
 */
public class CameraNectar extends CameraRGBIRDepth {

    private boolean getMode = false;

    public String DEFAULT_REDIS_HOST = "localhost";
    public int DEFAULT_REDIS_PORT = 6379;
    private DetectedMarker[] currentMarkers;
    private Jedis redisGet;
    private Jedis redisExternalGet;

    public CameraNectar(String cameraName) {
        this.cameraDescription = cameraName;
    }

    /**
     * Update the calibration from Nectar.
     */
    public boolean updateCalibration() {
        boolean set = false;
        if (useColor) {
            String key = this.cameraDescription + ":calibration";
            if (this.exists(key)) {
                this.colorCamera.setCalibration(JSONObject.parse(this.get(key)));
                set = true;
            }
        }
        if (useDepth) {
            String key = this.cameraDescription + ":depth:calibration";
            if (this.exists(key)) {
                this.depthCamera.setCalibration(JSONObject.parse(this.get(key)));
                set = false;
            }
        }
        return set;
    }

    public PMatrix3D getLocation(String key) {
        String keyTotal = this.getCameraDescription() + ":" + key;
        PMatrix3D table = new PMatrix3D();
        if (this.exists(keyTotal)) {
            table = ProjectiveDeviceP.JSONtoPMatrix(
                    JSONArray.parse(
                            this.get(
                                    this.getCameraDescription() + ":table")));
        }
        return table;
    }

    public PMatrix3D getTableLocation() {
        return getLocation("table");
    }

    @Override
    public void start() {
        try {
            redisGet = createConnection();
            redisExternalGet = createConnection();
            if (useColor) {
                startRGB();
                startMarkerTracking();
            }
            if (useDepth) {
                System.out.println("NECTAR get depth.");
                startDepth();
            }

            this.isConnected = true;
        } catch (NumberFormatException e) {
            System.err.println("Could not start Nectar camera: " + cameraDescription + ". " + e);
            System.err.println("Maybe the input key is not correct.");
            System.exit(-1);
        } catch (Exception e) {
            System.err.println("Could not start Nectar camera: " + cameraDescription + ". " + e);
            System.err.println("Check cable connection, ID and resolution asked.");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void startRGB() {
        Jedis redis = createConnection();

        int w = Integer.parseInt(redis.get(cameraDescription + ":width"));
        int h = Integer.parseInt(redis.get(cameraDescription + ":height"));
        String format = redis.get(cameraDescription + ":pixelformat");
        colorCamera.setSize(w, h);

        if (format != null) {
            colorCamera.setPixelFormat(PixelFormat.valueOf(format));
        } else {
            colorCamera.setPixelFormat(PixelFormat.RGB);
        }
        colorCamera.setFrameRate(30);
        colorCamera.isConnected = true;

        // Load the calibration... 
        String calibration = redis.get(cameraDescription + ":calibration");
        if (calibration == null) {
            System.err.println("Could not find camera calibration at key `" + cameraDescription + ":calibration`.");
            System.exit(-1);
        }
        JSONObject calib = JSONObject.parse(calibration);
        colorCamera.setCalibration(calib);

        if (!getMode) {
            new RedisThread(redis, new ImageListener(colorCamera.getPixelFormat()), cameraDescription).start();
        }
    }

    /**
     * Fetch the calibrations(intrinsics) from Redis / Nectar.
     */
    public void updateCalibrations() {
        try {
            JSONObject calib = JSONObject.parse(this.get(cameraDescription + ":calibration"));
            colorCamera.setCalibration(calib);
            JSONObject calib2 = JSONObject.parse(this.get(cameraDescription + ":depth:calibration"));
            depthCamera.setCalibration(calib2);
        } catch (Exception e) {
            System.out.println("cannot load camera and depth camera calibrations: " + e );
            e.printStackTrace();
        }
    }

    /**
     * Fetch the extrinsics (color-depth) from Redis / Nectar.
     * @param connection optionnal can be null
     */
    public void updateExtrinsics(Jedis connection) {
        PMatrix3D extr;
        if (connection != null) {
            extr = ProjectiveDeviceP.JSONtoPMatrix(JSONArray.parse(connection.get(cameraDescription + ":extrinsics:depth")));
        } else {
            extr = ProjectiveDeviceP.JSONtoPMatrix(JSONArray.parse(this.get(cameraDescription + ":extrinsics:depth")));
        }
        // set extrinsics...
        depthCamera.setExtrinsics(extr);
    }

    /**
     * Create a new connection. 
     * @return 
     */
    public Jedis createConnection() {
        return new Jedis(DEFAULT_REDIS_HOST, DEFAULT_REDIS_PORT);
    }

    private void startDepth() {
        Jedis redis2 = new Jedis(DEFAULT_REDIS_HOST, DEFAULT_REDIS_PORT);

        String v = redis2.get(cameraDescription + ":depth:width");
        if (v != null) {

//        if (redis.exists(cameraDescription + ":depth:width")) {
            int w = Integer.parseInt(redis2.get(cameraDescription + ":depth:width"));
            int h = Integer.parseInt(redis2.get(cameraDescription + ":depth:height"));
            depthCamera.setSize(w, h);
            depthCamera.setFrameRate(30);
            depthCamera.isConnected = true;
            // TODO: Standard Depth format
            depthCamera.setPixelFormat(PixelFormat.OPENNI_2_DEPTH);

            updateExtrinsics(redis2);

            try {
                // set extrinsics...
                PMatrix3D extr = ProjectiveDeviceP.JSONtoPMatrix(JSONArray.parse(redis2.get(cameraDescription + ":extrinsics:depth")));
                depthCamera.setExtrinsics(extr);
            } catch (Exception e) {
                System.out.println("Could not load extrinsics: " + e);
            }
            if (!getMode) {
                new RedisThread(redis2, new ImageListener(depthCamera.getPixelFormat()), cameraDescription + ":depth:raw").start();
            }
        }
    }

    public void startMarkerTracking() {
        Jedis redis = new Jedis(DEFAULT_REDIS_HOST, DEFAULT_REDIS_PORT);
        if (!getMode) {
            new RedisThread(redis, new MarkerListener(), cameraDescription + ":markers").start();
        }
    }

    private void setMarkers(byte[] message) {
        currentMarkers = parseMarkerList(new String(message));
//        lastMarkers = currentMarkers;
        super.setMarkers(currentMarkers);
//        System.out.println("Markers found: " + currentMarkers.length);
    }

    public DetectedMarker[] getMarkers() {
        if (currentMarkers == null) {
            return new DetectedMarker[0];
        }
        return currentMarkers;
    }

    /**
     * Switch to get Mode. It will read the key using the redis get command,
     * instead of pub/sub.
     *
     * @param get
     */
    public void setGetMode(boolean get) {
        this.getMode = get;
    }

    @Override
    public void grab() {
        if (this.isClosing()) {
            return;
        }
        try {
            if (getMode) {

                if (useColor) {
                    setMarkers(redisGet.get((cameraDescription + ":markers").getBytes()));
                    setColorImage(redisGet.get(cameraDescription.getBytes()));
                }
                if (useDepth) {
                    setDepthImage(redisGet.get((cameraDescription + ":depth:raw").getBytes()));
                }
                // sleep only
                Thread.sleep(15);
            } else {
                //..nothing the princess is in another thread.
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            System.err.println("CameraNectar grab Error ! " + e);
        } catch (Exception e) {
            System.err.println("Camera Nectar error:  " + e);
            e.printStackTrace();
        }
    }

    private opencv_core.IplImage rawVideoImage = null;
    private opencv_core.IplImage rawDepthImage = null;

    protected void setColorImage(byte[] message) {
        int channels = 3;
        if (rawVideoImage == null || rawVideoImage.width() != colorCamera.width || rawVideoImage.height() != colorCamera.height) {
            rawVideoImage = opencv_core.IplImage.create(colorCamera.width, colorCamera.height, IPL_DEPTH_8U, 3);
        }
        int frameSize = colorCamera.width * colorCamera.height * channels;
        rawVideoImage.getByteBuffer().put(message, 0, frameSize);
        colorCamera.updateCurrentImage(rawVideoImage);
//        colorCamera.updateCurrentImage(rawVideoImage);

        this.setChanged();
        this.notifyObservers("image");
    }

    protected void setDepthImage(byte[] message) {
        int iplDepth = IPL_DEPTH_8U;
        int channels = 2;

        int frameSize = depthCamera.width * depthCamera.height * channels;
        // TODO: Handle as a sort buffer instead of byte.
        if (rawDepthImage == null || rawDepthImage.width() != depthCamera.width || rawDepthImage.height() != depthCamera.height) {
            rawDepthImage = opencv_core.IplImage.create(depthCamera.width, depthCamera.height, iplDepth, channels);
        }
        rawDepthImage.getByteBuffer().put(message, 0, frameSize);
        depthCamera.updateCurrentImage(rawDepthImage);

        // TODO: Send Touch Event ?
        if (getActingCamera() == IRCamera) {
//            ((WithTouchInput) depthCamera).newTouchImageWithColor(IRCamera.currentImage);
            return;
        }
        if (getActingCamera() == colorCamera || useColor && colorCamera.currentImage != null) {
//            ((WithTouchInput) depthCamera).newTouchImageWithColor(colorCamera.currentImage);
            return;
        }
//        ((WithTouchInput) depthCamera).newTouchImage();

    }

    @Override
    public void close() {
        this.setClosing();
    }

    @Override
    protected void grabIR() {
    }

    @Override
    protected void grabDepth() {
    }

    @Override
    protected void grabColor() {
    }

    @Override
    protected void internalStart() throws Exception {
    }

    @Override
    protected void internalGrab() throws Exception {
    }

    class RedisThread extends Thread {

        BinaryJedisPubSub listener;
        private final String key;
        Jedis client;

        public RedisThread(Jedis client, BinaryJedisPubSub listener, String key) {
            this.listener = listener;
            this.key = key;
            this.client = client;
        }

        public void run() {
            while (!isClosing()) {
                try {
                    byte[] id = key.getBytes();
                    client.subscribe(listener, id);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Redis connection error: " + e);
                    System.out.println("Retrying to connect...");
                    client.close();
                    client = createConnection();
                }
            }
        }
    }

    private Jedis checkConnection(Jedis connection) {
        if (connection == null || !connection.isConnected()) {
            connection = createConnection();
        }
        return connection;
    }

    class ImageListener extends BinaryJedisPubSub {

        PixelFormat format;
        Jedis getConnection;

        public ImageListener(PixelFormat format) {
            this.format = format;
            getConnection = createConnection();
        }

        @Override
        public void onMessage(byte[] channel, byte[] message) {
            try {
                getConnection = checkConnection(getConnection);
                if (this.format == PixelFormat.BGR || this.format == PixelFormat.RGB) {
                    byte[] data = getConnection.get(channel);
                    setColorImage(data);
                }
                if (this.format == PixelFormat.OPENNI_2_DEPTH) {
                    byte[] data = getConnection.get(channel);
                    setDepthImage(data);
//                System.out.println("received depth message image");

                }
            } catch (Exception e) {
                System.out.println("Exception reading data: ");
                e.printStackTrace();
            }
        }

        @Override
        public void onSubscribe(byte[] channel, int subscribedChannels) {
        }

        @Override
        public void onUnsubscribe(byte[] channel, int subscribedChannels) {
        }

        @Override
        public void onPSubscribe(byte[] pattern, int subscribedChannels) {
        }

        @Override
        public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {
        }

        @Override
        public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {
        }
    }

    class MarkerListener extends BinaryJedisPubSub {

        public MarkerListener() {
        }

        @Override
        public void onMessage(byte[] channel, byte[] message) {
            setMarkers(message);
        }

        @Override
        public void onSubscribe(byte[] channel, int subscribedChannels) {
        }

        @Override
        public void onUnsubscribe(byte[] channel, int subscribedChannels) {
        }

        @Override
        public void onPSubscribe(byte[] pattern, int subscribedChannels) {
        }

        @Override
        public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {
        }

        @Override
        public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {
        }
    }

    public static DetectedMarker[] parseMarkerList(String jsonMessage) {

        DetectedMarker detectedMarkers[] = new DetectedMarker[0];
//        Marker m = new Marker(0, corners);
        JSONObject msg = null;
        try {
            msg = JSONObject.parse(jsonMessage);
        } catch (Exception e) {
            System.err.println("Exception while parsing json." + e.toString() + " \nMessage: " + jsonMessage);
        }
        if (msg == null) {
            return detectedMarkers;
        }
//        System.out.println("json: " + msg.getJSONArray("markers").size());

        JSONArray markers = msg.getJSONArray("markers");

        if (markers != null && markers.size() > 0) {
            detectedMarkers = new DetectedMarker[markers.size()];
            for (int i = 0; i < markers.size(); i++) {
                JSONObject m = markers.getJSONObject(i);

                int id = m.getInt("id");
                JSONArray corners = m.getJSONArray("corners");

                assert (corners.size() == 8);
//                System.out.println("Corners size: " + corners.size());
                DetectedMarker dm = new DetectedMarker(id,
                        corners.getFloat(0),
                        corners.getFloat(1),
                        corners.getFloat(2),
                        corners.getFloat(3),
                        corners.getFloat(4),
                        corners.getFloat(5),
                        corners.getFloat(6),
                        corners.getFloat(7));

                detectedMarkers[i] = dm;
            }
        }
        return detectedMarkers;
    }

    public synchronized String set(String key, String value) {
        return redisExternalGet.set(key, value);
    }

    public synchronized String set(String key, String value, String nxxx, String expx, long time) {
        return redisExternalGet.set(key, value, nxxx, expx, time);
    }

    public synchronized String get(String key) {
        return redisExternalGet.get(key);
    }

    public synchronized Long exists(String... keys) {
        return redisExternalGet.exists(keys);
    }

    public synchronized Boolean exists(String key) {
        return redisExternalGet.exists(key);
    }

    public synchronized Set<String> keys(String pattern) {
        return redisExternalGet.keys(pattern);
    }

    public synchronized String rename(String oldkey, String newkey) {
        return redisExternalGet.rename(oldkey, newkey);
    }

    public synchronized Long renamenx(String oldkey, String newkey) {
        return redisExternalGet.renamenx(oldkey, newkey);
    }

    public synchronized Long move(String key, int dbIndex) {
        return redisExternalGet.move(key, dbIndex);
    }

    public synchronized String getSet(String key, String value) {
        return redisExternalGet.getSet(key, value);
    }

    public synchronized List<String> mget(String... keys) {
        return redisExternalGet.mget(keys);
    }

    public synchronized Long setnx(String key, String value) {
        return redisExternalGet.setnx(key, value);
    }

    public synchronized String setex(String key, int seconds, String value) {
        return redisExternalGet.setex(key, seconds, value);
    }

    public synchronized String mset(String... keysvalues) {
        return redisExternalGet.mset(keysvalues);
    }

    public synchronized Long msetnx(String... keysvalues) {
        return redisExternalGet.msetnx(keysvalues);
    }

    public synchronized Long incrBy(String key, long integer) {
        return redisExternalGet.incrBy(key, integer);
    }

    public synchronized Double incrByFloat(String key, double value) {
        return redisExternalGet.incrByFloat(key, value);
    }

    public synchronized Long incr(String key) {
        return redisExternalGet.incr(key);
    }

    public synchronized Long append(String key, String value) {
        return redisExternalGet.append(key, value);
    }

    public synchronized Long hset(String key, String field, String value) {
        return redisExternalGet.hset(key, field, value);
    }

    public synchronized String hget(String key, String field) {
        return redisExternalGet.hget(key, field);
    }

    public synchronized Long hsetnx(String key, String field, String value) {
        return redisExternalGet.hsetnx(key, field, value);
    }

    public synchronized String hmset(String key, Map<String, String> hash) {
        return redisExternalGet.hmset(key, hash);
    }

    public synchronized List<String> hmget(String key, String... fields) {
        return redisExternalGet.hmget(key, fields);
    }

    public synchronized Long hincrBy(String key, String field, long value) {
        return redisExternalGet.hincrBy(key, field, value);
    }

    public synchronized Double hincrByFloat(String key, String field, double value) {
        return redisExternalGet.hincrByFloat(key, field, value);
    }

    public synchronized Boolean hexists(String key, String field) {
        return redisExternalGet.hexists(key, field);
    }

    public synchronized Long hdel(String key, String... fields) {
        return redisExternalGet.hdel(key, fields);
    }

    public synchronized Long hlen(String key) {
        return redisExternalGet.hlen(key);
    }

    public synchronized Set<String> hkeys(String key) {
        return redisExternalGet.hkeys(key);
    }

    public synchronized List<String> hvals(String key) {
        return redisExternalGet.hvals(key);
    }

    public synchronized Map<String, String> hgetAll(String key) {
        return redisExternalGet.hgetAll(key);
    }

    public synchronized Long rpush(String key, String... strings) {
        return redisExternalGet.rpush(key, strings);
    }

    public synchronized Long lpush(String key, String... strings) {
        return redisExternalGet.lpush(key, strings);
    }

    public synchronized Long llen(String key) {
        return redisExternalGet.llen(key);
    }

    public synchronized List<String> lrange(String key, long start, long end) {
        return redisExternalGet.lrange(key, start, end);
    }

    public synchronized String ltrim(String key, long start, long end) {
        return redisExternalGet.ltrim(key, start, end);
    }

    public synchronized String lindex(String key, long index) {
        return redisExternalGet.lindex(key, index);
    }

    public synchronized String lset(String key, long index, String value) {
        return redisExternalGet.lset(key, index, value);
    }

    public synchronized Long lrem(String key, long count, String value) {
        return redisExternalGet.lrem(key, count, value);
    }

    public synchronized String lpop(String key) {
        return redisExternalGet.lpop(key);
    }

    public synchronized String rpop(String key) {
        return redisExternalGet.rpop(key);
    }

    public synchronized String rpoplpush(String srckey, String dstkey) {
        return redisExternalGet.rpoplpush(srckey, dstkey);
    }

    public synchronized Long sadd(String key, String... members) {
        return redisExternalGet.sadd(key, members);
    }

    public synchronized Long srem(String key, String... members) {
        return redisExternalGet.srem(key, members);
    }

    public synchronized String spop(String key) {
        return redisExternalGet.spop(key);
    }

    public synchronized Set<String> spop(String key, long count) {
        return redisExternalGet.spop(key, count);
    }

    public synchronized Long scard(String key) {
        return redisExternalGet.scard(key);
    }

    public synchronized Boolean sismember(String key, String member) {
        return redisExternalGet.sismember(key, member);
    }

    public synchronized Set<String> sinter(String... keys) {
        return redisExternalGet.sinter(keys);
    }

    public synchronized Long sinterstore(String dstkey, String... keys) {
        return redisExternalGet.sinterstore(dstkey, keys);
    }

    public synchronized Long zadd(String key, double score, String member) {
        return redisExternalGet.zadd(key, score, member);
    }

    public synchronized Long zadd(String key, double score, String member, ZAddParams params) {
        return redisExternalGet.zadd(key, score, member, params);
    }

    public synchronized Long zadd(String key, Map<String, Double> scoreMembers) {
        return redisExternalGet.zadd(key, scoreMembers);
    }

    public synchronized Long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
        return redisExternalGet.zadd(key, scoreMembers, params);
    }

    public synchronized Set<String> zrange(String key, long start, long end) {
        return redisExternalGet.zrange(key, start, end);
    }

    public synchronized Double zincrby(String key, double score, String member) {
        return redisExternalGet.zincrby(key, score, member);
    }

    public synchronized Double zincrby(String key, double score, String member, ZIncrByParams params) {
        return redisExternalGet.zincrby(key, score, member, params);
    }

    public synchronized Long zcard(String key) {
        return redisExternalGet.zcard(key);
    }

    public synchronized List<String> sort(String key, SortingParams sortingParameters) {
        return redisExternalGet.sort(key, sortingParameters);
    }

    public synchronized List<String> blpop(int timeout, String... keys) {
        return redisExternalGet.blpop(timeout, keys);
    }

    public synchronized List<String> blpop(String... args) {
        return redisExternalGet.blpop(args);
    }

    public synchronized List<String> brpop(String... args) {
        return redisExternalGet.brpop(args);
    }

    public synchronized Long sort(String key, SortingParams sortingParameters, String dstkey) {
        return redisExternalGet.sort(key, sortingParameters, dstkey);
    }

    public synchronized Long sort(String key, String dstkey) {
        return redisExternalGet.sort(key, dstkey);
    }

    public synchronized List<String> brpop(int timeout, String... keys) {
        return redisExternalGet.brpop(timeout, keys);
    }

    public synchronized Long zcount(String key, double min, double max) {
        return redisExternalGet.zcount(key, min, max);
    }

    public synchronized Long zcount(String key, String min, String max) {
        return redisExternalGet.zcount(key, min, max);
    }

    public synchronized Long zinterstore(String dstkey, String... sets) {
        return redisExternalGet.zinterstore(dstkey, sets);
    }

    public synchronized Long zinterstore(String dstkey, ZParams params, String... sets) {
        return redisExternalGet.zinterstore(dstkey, params, sets);
    }

    public synchronized Long zlexcount(String key, String min, String max) {
        return redisExternalGet.zlexcount(key, min, max);
    }

    public synchronized Long lpushx(String key, String... string) {
        return redisExternalGet.lpushx(key, string);
    }

    public synchronized Long persist(String key) {
        return redisExternalGet.persist(key);
    }

    public synchronized Long rpushx(String key, String... string) {
        return redisExternalGet.rpushx(key, string);
    }

    public synchronized Long linsert(String key, BinaryClient.LIST_POSITION where, String pivot, String value) {
        return redisExternalGet.linsert(key, where, pivot, value);
    }

    public synchronized String brpoplpush(String source, String destination, int timeout) {
        return redisExternalGet.brpoplpush(source, destination, timeout);
    }

    public synchronized Boolean setbit(String key, long offset, boolean value) {
        return redisExternalGet.setbit(key, offset, value);
    }

    public synchronized Boolean setbit(String key, long offset, String value) {
        return redisExternalGet.setbit(key, offset, value);
    }

    public synchronized Boolean getbit(String key, long offset) {
        return redisExternalGet.getbit(key, offset);
    }

    public synchronized Long setrange(String key, long offset, String value) {
        return redisExternalGet.setrange(key, offset, value);
    }

    public synchronized String getrange(String key, long startOffset, long endOffset) {
        return redisExternalGet.getrange(key, startOffset, endOffset);
    }

    public synchronized Long bitpos(String key, boolean value) {
        return redisExternalGet.bitpos(key, value);
    }

    public synchronized Long bitpos(String key, boolean value, BitPosParams params) {
        return redisExternalGet.bitpos(key, value, params);
    }

    public synchronized Long publish(String channel, String message) {
        return redisExternalGet.publish(channel, message);
    }

    public synchronized Long bitcount(String key) {
        return redisExternalGet.bitcount(key);
    }

    public synchronized Long bitcount(String key, long start, long end) {
        return redisExternalGet.bitcount(key, start, end);
    }

    public synchronized Long bitop(BitOP op, String destKey, String... srcKeys) {
        return redisExternalGet.bitop(op, destKey, srcKeys);
    }

    public synchronized String restore(String key, int ttl, byte[] serializedValue) {
        return redisExternalGet.restore(key, ttl, serializedValue);
    }

    public synchronized String set(String key, String value, String nxxx) {
        return redisExternalGet.set(key, value, nxxx);
    }

    public synchronized String set(String key, String value, String nxxx, String expx, int time) {
        return redisExternalGet.set(key, value, nxxx, expx, time);
    }

    public synchronized ScanResult<String> scan(String cursor) {
        return redisExternalGet.scan(cursor);
    }

    public synchronized ScanResult<String> scan(String cursor, ScanParams params) {
        return redisExternalGet.scan(cursor, params);
    }

    public synchronized ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
        return redisExternalGet.hscan(key, cursor);
    }

    public synchronized ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        return redisExternalGet.hscan(key, cursor, params);
    }

    public synchronized ScanResult<String> sscan(String key, String cursor) {
        return redisExternalGet.sscan(key, cursor);
    }

    public synchronized ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        return redisExternalGet.sscan(key, cursor, params);
    }

    public synchronized List<String> blpop(int timeout, String key) {
        return redisExternalGet.blpop(timeout, key);
    }

    public synchronized List<String> brpop(int timeout, String key) {
        return redisExternalGet.brpop(timeout, key);
    }

}
