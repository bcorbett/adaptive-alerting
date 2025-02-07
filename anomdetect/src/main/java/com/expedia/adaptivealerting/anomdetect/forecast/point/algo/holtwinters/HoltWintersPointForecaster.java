/*
 * Copyright 2018-2019 Expedia Group, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expedia.adaptivealerting.anomdetect.forecast.point.algo.holtwinters;

import com.expedia.adaptivealerting.anomdetect.forecast.point.PointForecast;
import com.expedia.adaptivealerting.anomdetect.forecast.point.PointForecaster;
import com.expedia.metrics.MetricData;
import lombok.Generated;
import lombok.Getter;
import lombok.val;

import static com.expedia.adaptivealerting.anomdetect.util.AssertUtil.notNull;
import static java.lang.String.format;

// TODO Rename to HoltWintersPointForecaster [WLW]
public class HoltWintersPointForecaster implements PointForecaster {

    @Getter
    @Generated // https://reflectoring.io/100-percent-test-coverage/
    private HoltWintersPointForecasterParams params;

    @Getter
    @Generated // https://reflectoring.io/100-percent-test-coverage/
    private HoltWintersOnlineComponents components;

    private HoltWintersSimpleTrainingModel holtWintersSimpleTrainingModel;
    private HoltWintersOnlineAlgorithm holtWintersOnlineAlgorithm;

    public HoltWintersPointForecaster(HoltWintersPointForecasterParams params) {
        notNull(params, "params can't be null");
        params.validate();
        this.params = params;

        this.components = new HoltWintersOnlineComponents(params);
        this.holtWintersOnlineAlgorithm = new HoltWintersOnlineAlgorithm();
        this.holtWintersSimpleTrainingModel = new HoltWintersSimpleTrainingModel(params);

        val initForecast = holtWintersOnlineAlgorithm.getForecast(
                params.getSeasonalityType(),
                components.getLevel(),
                components.getBase(),
                components.getSeasonal(components.getCurrentSeasonalIndex()));
        components.setForecast(initForecast);
    }

    @Override
    public PointForecast forecast(MetricData metricData) {
        notNull(metricData, "metricData can't be null");
        try {
            double prevForecast = components.getForecast();
            trainOrObserve(metricData.getValue());
            return new PointForecast(prevForecast, stillWarmingUp());
        } catch (Exception e) {
            throw new HoltWintersException(
                    format("Exception occurred during classification. %s: \"%s\"", e.getClass(), e.getMessage()), e);
        }
    }

    public boolean isInitialTrainingComplete() {
        switch (params.getInitTrainingMethod()) {
            case NONE:
                return true;
            case SIMPLE:
                return holtWintersSimpleTrainingModel.isTrainingComplete(params);
            default:
                // This shouldn't happen
                throw new IllegalStateException(format("Unexpected training method '%s'", params.getInitTrainingMethod()));
        }
    }

    private void trainOrObserve(double observed) {
        if (!isInitialTrainingComplete()) {
            holtWintersSimpleTrainingModel.observeAndTrain(observed, params, components);
        } else {
            holtWintersOnlineAlgorithm.observeValueAndUpdateForecast(observed, params, components);
        }
    }

    private boolean stillWarmingUp() {
        return components.getN() <= params.getWarmUpPeriod();
    }

}
