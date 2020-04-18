package rdma;

import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.RdmaServerEndpoint;
import com.ibm.disni.util.DiSNILogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ServerConnectionEndpoint implements Runnable{
    private final RdmaActiveEndpointGroup<MapperEndpoint> endpointGroup;
    private final RdmaServerEndpoint<MapperEndpoint> serverEndpoint;

    // This queue contains endpoints that have already receives the information from its corresponding reducer
    private final ArrayBlockingQueue<MapperEndpoint> pendingRequestsFromReducer;

    private final ExecutorService executorService;


    ServerConnectionEndpoint(InetSocketAddress addr) throws Exception {
        // create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and
        // delivers CQ event to the endpoint.dispatchCqEvent() method.
        MapperServerEndpointFactory factory = new MapperServerEndpointFactory();
        endpointGroup = factory.getEndpointGroup();
        serverEndpoint = endpointGroup.createServerEndpoint();
        pendingRequestsFromReducer = new ArrayBlockingQueue<>(100);

        serverEndpoint.bind(addr, 10);
        DiSNILogger.getLogger().info("Server bound to address" + addr.toString());

//        ByteBuffer totalBuffer = ByteBuffer.allocate(RdmaConfigs.TOTAL_BUFFER_SIZE);
//        totalBuffer.clear()
//        IbvMr totalMr = serverEndpoint.registerMemory(totalBuffer).execute().getMr();

        executorService = Executors.newFixedThreadPool(1);
        executorService.submit(new RdmaProcess(pendingRequestsFromReducer));
    }

    @Override
    public void run() {
        while (true) {
            try {
                DiSNILogger.getLogger().info("waiting for connection...");
                MapperEndpoint endpoint1 = serverEndpoint.accept();
                DiSNILogger.getLogger().info("connection accepted from " + endpoint1.getDstAddr());
                endpoint1.initReceiving(pendingRequestsFromReducer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void close() throws IOException, InterruptedException {
        serverEndpoint.close();
        endpointGroup.close();
    }

}