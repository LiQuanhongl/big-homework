package HTTPServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread{
    private int port;
    private ServerSocket serverSocket;

    public Server(int port){
        this.port=port;
    }

    public static void main(String[] args){
        Server server = new Server(8888);
        server.start();
    }

//过程：建立连接、处理请求报文、发送应答报文
    public void run(){
        try {
            serverSocket = new ServerSocket(port);
            while(true) {
                System.out.println("~~Waiting for a connection~~");
                Socket socket = serverSocket.accept();
                System.out.println("Connected.");
                InputStream inputStream = socket.getInputStream();
                OutputStream outputStream = socket.getOutputStream();
                while (true) {
                    //等待请求报文
                    while (true) {
                        if (inputStream.available() != 0) {
                            break;
                        }
                    }
                    byte[] bytes = new byte[inputStream.available()];
                    inputStream.read(bytes);
                    System.out.println("Get a new request message.");
                    //HTTPServerHandler类处理收到的报文
                    HTTPServerHandler handler = new HTTPServerHandler(bytes);
                    System.out.println("This is the request message：");
                    System.out.println(new String(bytes)+"\n\n");
                    System.out.println("Sending the response message:");
                    //获得响应报文并发送
                    byte[] response = handler.Response();
                    System.out.println(new String(response)+"\n\n");
                    outputStream.write(response);
                    outputStream.flush();
                    System.out.println("Sending the message successfully.");
                    if (handler.isClosed()) {//是否长连接（默认开启长连接，请求报文的首部Connection为close则关闭长连接）
                        socket.close();
                        System.out.println("Close the connection.\n\n");
                        break;
                    }
                    System.out.println("Keeping the connection.\n\n");
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            System.out.println("The server has some errors.");
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}
