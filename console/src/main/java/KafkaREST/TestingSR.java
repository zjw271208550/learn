package KafkaREST;

import org.apache.commons.lang3.time.StopWatch;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static KafkaREST.KafkaRestTool.*;

/**
 * 测试 Kafka-rest 的发送效率
 * 目标 Topic 为 rest_test(partition_num:1)
 */
public class TestingSR {
    public static String TOPIC = "rest_test";
    public static String REST_HOST = "172.16.32.41";
    public static int REST_PORT = 8085;
    public static String CLIENT_HOST = "172.16.32.41";
    public static int CLIENT_PORT = 9095;
    public static String CONTENT_TYPE = "application/vnd.kafka.binary.v2+json";
    public static String ENCODE = "utf-8";
    public static int[] DATA_SIZE = {1,10,100,500,1000,2000,5000};
    public static int[] POOL_SIZE = {10,16,32,64,100,150};

    public static void main(String[] args) throws Exception{
        /**
         * 测试一批数据逐条发送效率和成批发送效率
         */
        for(int size : DATA_SIZE) {
            System.out.println("<============== Data "+size+" =============>");
            HashMap<String, String> data = new HashMap<>();
            for (int i = 0; i < size; i++) {
                data.put("key" + i, "value" + i);
            }
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<Long> f1 =  executor.submit(new Send1Thread(data));
            Future<Long> f2 =  executor.submit(new SendAllThread(data));
            long rel1 = f1.get();
            long rel2 = f2.get();
            System.out.println("Rest Send 1 for "+data.size()+" use : "+rel1);
            System.out.println("Rest Send all for "+data.size()+" use : "+rel2);
            executor.shutdown();
        }

        /**
         * 测试一批数据逐条发送效率
         */
        KafkaClientTool<String,String> producer = new KafkaClientTool<>(CLIENT_HOST,CLIENT_PORT);
        for(int size : DATA_SIZE) {
            System.out.println("<============== Data "+size+" =============>");
            HashMap<String, String> data = new HashMap<>();
            for (int i = 0; i < size; i++) {
                data.put("key" + i, "value" + i);
            }
            ExecutorService executor = Executors.newFixedThreadPool(1);
            Future<Long> f1 =  executor.submit(new ClientSend1Thread(producer,data));
            long rel = f1.get();
            System.out.println("Client Send 1 for "+data.size()+" use : "+rel);
            executor.shutdown();
        }

        /**
         * 测试 1W条数据并发单条发送效率
         */
        HashMap<Integer,String> data = new HashMap<>();
        for(int i=0; i<10000;i++){
            data.put(i,"aaaaaaaaaa");
        }
        StopWatch stopWatch = new StopWatch();
        for(int size: POOL_SIZE){
            stopWatch.start();
            ExecutorService executor = Executors.newFixedThreadPool(size);
            LinkedList<Future> futures = new LinkedList<>();
            for(int key:data.keySet()){
                futures.add(
                    executor.submit(
                            new SendThread(String.valueOf(key),data.get(key))
                    )
                );
            }
            for(Future f:futures){
                f.get();
            }
            executor.shutdown();
            stopWatch.stop();
            System.out.println("Send 1 for "+data.size()+" with "+size+" threads use : "+stopWatch.getTime());
            stopWatch.reset();
        }

        HttpClientPoolTool.closeConnectionPool();

    }


    /**
     * Kafka-rest逐条发送线程
     */
    public static class Send1Thread  implements Callable<Long> {
        private HashMap<String,String> data;

        public Send1Thread(HashMap<String,String> data) {
            this.data = data;
        }

        @Override
        public Long call() {
            StopWatch stopWatch = new StopWatch();
            long time = 0L;
            for(Map.Entry<String,String> entry:this.data.entrySet()){
                stopWatch.start();
                String rel = sendMessage(
                            REST_HOST,
                            REST_PORT,
                            TOPIC,
                            0,
                            entry.getKey(),
                            entry.getValue(),
                            CONTENT_TYPE,
                            ENCODE);
                stopWatch.stop();
                time = time + stopWatch.getTime();
                stopWatch.reset();
                if(KafkaRestTool.checkSuccess(rel)){
                   System.out.print("$FAIL$");
                }
            }
            return time;
        }
    }

    /**
     * Kafka-rest成批发送线程
     */
    public static class SendAllThread  implements Callable<Long> {
        private HashMap<String,String> data;

        public SendAllThread(HashMap<String,String> data) {
            this.data = data;
        }

        @Override
        public Long call() {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            String rel = sendMessageAll(
                    REST_HOST,
                    REST_PORT,
                    TOPIC,
                    0,
                    this.data,
                    CONTENT_TYPE,
                    ENCODE);
            if(KafkaRestTool.checkSuccess(rel)){
                System.out.print("$FAIL$");
            }
            stopWatch.stop();
            return stopWatch.getTime();
        }
    }

    /**
     * Kafka-rest单条条发送线程
     */
    public static class SendThread  implements Callable<Long> {
        private String key;
        private String value;

        public SendThread(String key,String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Long call() {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            String rel = sendMessage(
                    REST_HOST,
                    REST_PORT,
                    TOPIC,
                    0,
                    this.key,
                    this.value,
                    CONTENT_TYPE,
                    ENCODE);
            stopWatch.stop();
            if(KafkaRestTool.checkSuccess(rel)){
                System.out.print("$FAIL$");
            }
            return stopWatch.getTime();
        }


    }

    /**
     * Kafka-client逐条发送线程
     */
    public static class ClientSend1Thread  implements Callable<Long>{
        private KafkaClientTool<String,String> producer;
        private HashMap<String,String> data;

        public ClientSend1Thread(KafkaClientTool<String,String> producer,HashMap<String,String> data) {
            this.producer = producer;
            this.data = data;
        }

        @Override
        public Long call() {
            StopWatch stopWatch = new StopWatch();
            long time = 0L;
            for(Map.Entry<String,String> entry:this.data.entrySet()){
                stopWatch.start();
                this.producer.sendMessage(
                                TOPIC,
                                0,
                                entry.getKey(),
                                entry.getValue());
                stopWatch.stop();
                time = time + stopWatch.getTime();
                stopWatch.reset();
            }
            return time;
        }
    }

    public static HashMap<String,String> getDataUse(int threadId,int poolsize,HashMap<Integer,String> data){
        HashMap<String,String> rel = new HashMap<>();
        for(int i:data.keySet()){
            if( i%poolsize == threadId) {
                rel.put(String.valueOf(i), data.get(i));
            }
        }
        return rel;
    }
}
