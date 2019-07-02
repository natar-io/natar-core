/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tech.lity.rea.nectar.camera;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import static processing.core.PConstants.ARGB;
import static processing.core.PConstants.RGB;
import processing.core.PImage;
import processing.data.JSONObject;
import redis.clients.jedis.Jedis;

/**
 *
 * @author Jeremy Laviole
 */
public class VideoEmitter extends RedisClientImpl {

    Jedis redis, redisSend;

    private String output = "image";
    private PImage imageRef;
    private int colorImageCount;

    public VideoEmitter() {
    }

    public VideoEmitter(String host, int port, String auth, String key) {
        this.setRedisHost(host);
        this.setRedisPort(port);
        this.setRedisAuth(auth);
        this.output = key;
        redis = createConnection();
    }

    public void setReference(PImage img) {
        imageRef = img;
        sendParams(img);
    }

    public void sendImage(PImage img, int time) {
        if (imageRef == null || img.width != imageRef.width || img.height != imageRef.height) {
            setReference(img);
        }

        img.loadPixels();
        byte[] imageData;
        try {
            imageData = integersToBytes(img.pixels);
        } catch (IOException ex) {
            Logger.getLogger(VideoEmitter.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("Cannot convert image to bytes.");
            return;
        }

        colorImageCount++;

        String name = output;
        byte[] id = name.getBytes();
        JSONObject imageInfo = new JSONObject();
        imageInfo.setLong("timestamp", time);
        imageInfo.setLong("imageCount", colorImageCount);
        redis.set(id, imageData);
        redis.publish(id, imageInfo.toString().getBytes());
    }

    byte[] integersToBytes(int[] values) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (int i = 0; i < values.length; ++i) {
            dos.writeInt(values[i]);
        }

        return baos.toByteArray();
    }


    private void sendParams(PImage img) {
        redis.set(output + ":width", Integer.toString(img.width));
        redis.set(output + ":height", Integer.toString(img.height));
        redis.set(output + ":channels", Integer.toString(4));
        redis.clientSetname("VideoEmitter");
        
        if (img.format == RGB) {
            redis.set(output + ":pixelformat", Camera.PixelFormat.RGB.toString());
        }

        if (img.format == ARGB) {
            redis.set(output + ":pixelformat", Camera.PixelFormat.ARGB.toString());
        }

    }
}
