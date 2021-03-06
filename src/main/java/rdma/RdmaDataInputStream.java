package rdma;

import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.util.DiSNILogger;
import com.ibm.disni.verbs.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RdmaDataInputStream extends InputStream {
    private ClientEndpoint endpoint;
    private int mapperId;
    private int reducerId;
    private int length;


    public RdmaDataInputStream(RdmaActiveEndpointGroup<ClientEndpoint> endpointGroup, InetSocketAddress address, int mapperId, int reducerId) throws Exception {
        endpoint = endpointGroup.createEndpoint();
        endpoint.connect(address, RdmaConfigs.TIMEOUT);
        InetSocketAddress _addr = (InetSocketAddress) endpoint.getDstAddr();
        DiSNILogger.getLogger().info("client connected, address " + _addr.toString());

        this.mapperId = mapperId;
        this.reducerId = reducerId;
        this.length = 0;
    }

    public void prepareInfo() {
        ByteBuffer sendBuffer = endpoint.getSendBuf();
        IbvMr dataMemoryRegion = endpoint.getDataMr();
        sendBuffer.clear();
        sendBuffer.putLong(dataMemoryRegion.getAddr());
        sendBuffer.putInt(dataMemoryRegion.getLkey());
        sendBuffer.putInt(this.mapperId);
        sendBuffer.putInt(this.reducerId);
        sendBuffer.putInt(this.length);
        sendBuffer.clear();
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    int i = 0;
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesWritten = 0;
        try {
            this.length = len;
            this.mapperId++;
            this.reducerId++;
            this.prepareInfo();

            endpoint.executePostSend();

            // wait until the RDMA SEND message to be sent
            IbvWC sendWc = endpoint.getSendCompletionEvents().take();
            DiSNILogger.getLogger().info("Send wr_id: " + sendWc.getWr_id() + " op: " + sendWc.getOpcode());
            DiSNILogger.getLogger().info("Sending" + i + " completed");

            // wait for the receive buffer received immediate value
            IbvWC recWc = endpoint.getWriteCompletionEvents().take();
            DiSNILogger.getLogger().info("wr_id: " + recWc.getWr_id() + " op: " + recWc.getOpcode());
            endpoint.executePostRecv();
            ByteBuffer dataBuf = endpoint.getDataBuf();

            DiSNILogger.getLogger().info("rdma.Client::Write" + i++ + " Completed notified by the immediate value");
            // len is at most buffer's size
            dataBuf.position(0);
            bytesWritten = Math.min(len, dataBuf.limit());
            dataBuf.get(b, 0, bytesWritten);

            DiSNILogger.getLogger().info("rdma.Client::memory is written by server: " + new String(b, 0, bytesWritten));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return bytesWritten;
    }


    public void closeEndpoint() throws IOException, InterruptedException {
        endpoint.close();
    }
}
