package com.alain898.course.realtimestreaming.course20.riskengine;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FlinkRiskEngineLocal extends FlinkRiskEngine{

    public static String[] createData() {
        List<String> source = new ArrayList<>();
        int samples = 10;
        for (int i = 0; i < samples; i++) {
            String application = "app003";
            long timestamp = System.currentTimeMillis();
            String event_type = "transaction";
            String payment_account = String.format("user%d", RandomUtils.nextInt(0, 5));
            String receiving_account = String.format("user%d", RandomUtils.nextInt(0, 3));
            float amount = RandomUtils.nextInt(0, 1001);
            String event = JSONObject.toJSONString(new Event(
                    application, timestamp, event_type, payment_account, receiving_account, amount));
            source.add(event);
            System.out.println(String.format("send event[%s]", event));
            Tools.sleep(1000);
        }
        return source.toArray(new String[0]);
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        DataStream<String> stream = env.fromElements(createData());

        DataStream counts = stream
                .map(new MapFunction<String, JSONObject>() {
                    @Override
                    public JSONObject map(String s) throws Exception {
                        if (StringUtils.isEmpty(s)) {
                            return new JSONObject();
                        }
                        return JSONObject.parseObject(s);
                    }
                })
                .flatMap(new EventSplitFunction())
                .keyBy(new KeySelector<JSONObject, String>() {
                    @Override
                    public String getKey(JSONObject value) throws Exception {
                        return value.getString("KEY_VALUE");
                    }
                })
                .map(new KeyEnrichFunction())
                .map(new FeatureEnrichFunction())
                .keyBy(new KeySelector<JSONObject, String>() {
                    @Override
                    public String getKey(JSONObject value) throws Exception {
                        return value.getString("EVENT_ID");
                    }
                })
                .flatMap(new FeatureReduceFunction())
                .map(new RuleBasedModeling());

        counts.print().setParallelism(1);
        env.execute("FlinkRiskEngineLocal");
    }

}
