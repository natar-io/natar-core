/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tech.lity.rea.nectar.camera;

import redis.clients.jedis.Jedis;

/**
 *
 * @author Jeremy Laviole
 */
public interface RedisClient {

    public String getRedisHost();

    public void setRedisHost(String redisHost);

    public int getRedisPort();

    public void setRedisPort(int redisPort);

    public void setRedisAuth(String auth);

    public Jedis createConnection();
}
