package me.server.pvpsystem;

import java.util.UUID;

public class Contract {
    public String id;
    public UUID owner;
    public String ownerName;
    public UUID target;
    public String targetName;
    public double amount;
    public long createdAt;
    public UUID taker;      // null = заказ свободен
    public String takerName;
}
