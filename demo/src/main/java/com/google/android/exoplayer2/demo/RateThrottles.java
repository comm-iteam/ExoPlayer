package com.google.android.exoplayer2.demo;

import android.util.Pair;

import java.util.ArrayList;

public class RateThrottles {

  public static ArrayList<Pair<Integer, Integer>> getPlain1mbps(){
    ArrayList<Pair<Integer, Integer>> throttling = new ArrayList<>();
    throttling.add(Pair.create(0, 1_000_000));
    return throttling;
  }

  public static ArrayList<Pair<Integer, Integer>> getPlain5mbps(){
    ArrayList<Pair<Integer, Integer>> throttling = new ArrayList<>();
    throttling.add(Pair.create(0, 5_000_000));
    return throttling;
  }

  public static ArrayList<Pair<Integer, Integer>> getPlain10mbps(){
    ArrayList<Pair<Integer, Integer>> throttling = new ArrayList<>();
    throttling.add(Pair.create(0, 10_000_000));
    return throttling;
  }

  public static ArrayList<Pair<Integer, Integer>> getPlain20mbps(){
    ArrayList<Pair<Integer, Integer>> throttling = new ArrayList<>();
    throttling.add(Pair.create(0, 20_000_000));
    return throttling;
  }

  public static ArrayList<Pair<Integer, Integer>> getPlainStairLoop01(){
    ArrayList<Pair<Integer, Integer>> throttling = new ArrayList<>();
    throttling.add(Pair.create(0, 2_000_000));
    throttling.add(Pair.create(100, 4_000_000));
    throttling.add(Pair.create(200, 8_000_000));
    throttling.add(Pair.create(300, 4_000_000));
    throttling.add(Pair.create(400, -1));
    return throttling;
  }

  public static ArrayList<Pair<Integer, Integer>> getPlainWithCut01(){
    ArrayList<Pair<Integer, Integer>> throttling = new ArrayList<>();
    throttling.add(Pair.create(0, 10_000_000));
    throttling.add(Pair.create(100, 500_000));
    throttling.add(Pair.create(110, -1));
    return throttling;
  }
}
