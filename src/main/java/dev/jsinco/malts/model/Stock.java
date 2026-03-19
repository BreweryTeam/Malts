package dev.jsinco.malts.model;

import lombok.Getter;
import org.bukkit.Material;

@Getter
public class Stock {

    private final Material material;
    private long lastUpdate;
    private int amount;

    public Stock(Material material, int amount, long lastUpdate) {
        this.material = material;
        this.amount = amount;
        this.lastUpdate = lastUpdate;
    }

    public Stock(Material material, int amount) {
        this(material, amount, System.currentTimeMillis());
    }

    public Stock(Material material) {
        this(material, 0);
    }

    private void update() {
        this.lastUpdate = System.currentTimeMillis();
    }

    public void increase(int amount) {
        this.amount += amount;
        this.update();
    }

    public void decrease(int amount) {
        this.amount -= amount;
        this.update();
    }

    public void setAmount(int amount) {
        this.amount = amount;
        this.update();
    }
}
