package dev.jsinco.malts.configuration.serdes;

import dev.jsinco.malts.enums.Driver;
import eu.okaeri.configs.schema.GenericsPair;
import eu.okaeri.configs.serdes.BidirectionalTransformer;
import eu.okaeri.configs.serdes.SerdesContext;
import lombok.NonNull;

public class DriverConstantTransformer extends BidirectionalTransformer<String, Driver> {
    @Override
    public GenericsPair<String, Driver> getPair() {
        return this.genericsPair(String.class, Driver.class);
    }

    @Override
    public Driver leftToRight(@NonNull String data, @NonNull SerdesContext serdesContext) {
        return Driver.fromName(data);
    }

    @Override
    public String rightToLeft(@NonNull Driver data, @NonNull SerdesContext serdesContext) {
        return data.toString();
    }
}
