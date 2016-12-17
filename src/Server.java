import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server
{
    Server(int port) throws IOException
    {
        ServerSocket ss = new ServerSocket(port);
        while(true)
        {
            Socket conn = ss.accept();
            System.out.println("New connection from " + conn.getInetAddress());
            Handler c = new Handler(conn);
            c.start ();
        }
    }
    public static void main (String args[]) throws IOException {
//        if (args.length != 1)
//            throw new RuntimeException ("Syntax: ChatServer <port>");
//        new Server (Integer.parseInt (args[0]));
        new Server (80);
    }
}
