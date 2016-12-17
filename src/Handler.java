import java.io.*;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Handler extends Thread
{
    protected final static Object locker = new Object();
    protected static Vector<Handler> handlers = new Vector();
    protected static volatile boolean game = false;
    protected static volatile String questionaire = null;
    protected static volatile int attempts;
    protected static volatile String original = "", current = "";

    protected Socket conn;
    protected String username = null;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected boolean admin = false;

    Handler(Socket connection) throws IOException
    {
        if(handlers.size() == 0)
        {
            admin = true;
        }
        conn = connection;
        in = new DataInputStream(new BufferedInputStream(connection.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));
    }

    @Override
    public void run()
    {
        try {
            synchronized(handlers)
            {
                handlers.addElement(this);
            }
            while (true) {
                if(game && username != null)
                {
                    wordGuessGame();
                }
                String request = in.readUTF();
                Matcher m = Pattern.compile("#(\\p{Lu}+)#(\\p{all}*)").matcher(request);
                String type = null, content = "UNDEFINED";
                if(m.find())
                {
                    type = m.group(1);
                    content = m.group(2);
                }
                if(type == null)
                {
                    System.out.println("Request type is not set. Original request: " + request);
                }
                else
                {
                    switch(type)
                    {
                        case "MESSAGE":
                            if(kick(content));
                            else if(content.contains("\\gamestart "))
                            {
                                if(!admin)
                                {
                                    respond("#MESSAGE#Only admin can start a game.");
                                    break;
                                }
                                Matcher matchGame = Pattern.compile(
                                        "\\\\gamestart\\s([\\p{Graph}\\p{Blank}]+)").matcher(content);
                                if(matchGame.find())
                                {
                                    String maybeGameLead = matchGame.group(1);
                                    if(!userExists(maybeGameLead))
                                    {
                                        respond("#MESSAGE#No such user.");
                                        break;
                                    }
                                    attempts = 0;
                                    game = true;
                                    broadcast("#WAKEUP#");
                                    questionaire = maybeGameLead;
                                }
                                else
                                {
                                    respond("#MESSAGE#server: Wrong format.");
                                }
                                break;
                            }
                            else toAll("#MESSAGE#" + username + ": " + content);
                            break;
                        case "JOIN":
                            if(userExists(content))
                            {
                                respond("#ERROR#USEREXISTS");
                            }
                            else
                            {
                                respond("#SUCCESSLOGIN#");
                                if(admin)
                                {
                                    respond("#YOUADMIN#");
                                }
                                username = content;
                                respond("#ACTIVEUSERS#" + getActiveUsers()); //send list of active users to new user
                                broadcast("#ADDUSER#" + username);
                            }
                            break;
                        case "WAKEUP":
                            break;
                        default:
                            System.out.println("Unpredicted request " + request);
                            break;
                    }
                }

                System.out.println("Request in chat (client " + username + "): " + request);

            }
        } catch (IOException ex) {
            ex.printStackTrace ();
        } finally {
            synchronized(handlers)
            {
                handlers.removeElement (this);
            }
            if(username != null && handlers.size() > 0)
            {
                broadcast("#RMUSER#" + username); //tell other users that this user left chat
                if(admin)
                {
                    admin = false;
                    Handler newAdmin = handlers.get(0);
                    newAdmin.admin = true;
                    newAdmin.respond("#YOUADMIN#");
                }
            }
            try {
                conn.close ();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    protected void wordGuessGame() throws IOException
    {
        if(questionaire.equals(username)) //game leader has to set mysterious word
        {
            synchronized(locker) //ensure that all other handlers can't proceed till word is set
            {
                respond("#MESSAGE#game: Admin started a game.!!!");
                respond("#MESSAGE#game: Set the word for game.");
                String request;
                do
                {
                    request = in.readUTF();
                }
                while("#WAKEUP#".equals(request) && game);
                Matcher m = Pattern.compile("#(\\p{Lu}+)#(\\p{all}*)").matcher(request);
                String type = null, content = null;
                if (m.find())
                {
                    type = m.group(1);
                    content = m.group(2);
                }
                if("MESSAGE".equals(type))
                {
                    original = content;
                    current = "";
                    for(int i = 0; i < original.length(); i++)
                    {
                        current += '#';
                    }
                }
                else
                {
                    game = false;
                    broadcast("#WAKEUP#");
                    toAll("#MESSAGE#game: Nobody wins, game is over.");
                }
            }
        }
        if(!questionaire.equals(username) && "".equals(original)) //wait till game leader sets word
        {
            System.out.println("User " + username + " is waiting");
            try
            {
                Thread.sleep(50); //be sure that game leader gets lock on object first
                synchronized(locker)
                {
                }
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            System.out.println("User " + username + " is not waiting");
        }
        respond("#MESSAGE#game: Word is chosen. Action!");
        while(game)
        {
            attempts++;
            if(attempts % 3 == 1)
            {
                current = openOneChar(current, original);
                toAll("#MESSAGE#game: Guess this word - " + current);
            }
            String request;
            do
            {
                request = in.readUTF();
            }
            while("#WAKEUP#".equals(request) && game);
            System.out.println("Request in chat (client " + username + "): " + request);
            Matcher m = Pattern.compile("#(\\p{Lu}+)#(\\p{all}*)").matcher(request);
            String type = null, content = "UNDEFINED";
            if(m.find())
            {
                type = m.group(1);
                content = m.group(2);
            }
            if(type == null)
            {
                System.out.println("Request type is not set. Original request: " + request);
            }
            else
            {
                if(kick(content));
                else
                {
                    if(original.equals(content))
                    {
                        if(game)
                        {
                            broadcast("#WAKEUP#");
                            toAll("#MESSAGE#game: " + username + " is a winner. Word was '" + original + "'");
                        }
                        game = false;
                        continue;
                    }
                    toAll("#MESSAGE#" + username + ": " + content);
                }
            }
        }
        original = "";
        current = "";
    }

    protected String openOneChar(String toOpen, String original)
    {
        int index;
        do
        {
            index = (int) Math.round(Math.random() * (original.length() - 1));
            System.out.println("Index: " + index);
        }
        while(toOpen.charAt(index) != '#');
        char[] symbols = toOpen.toCharArray();
        symbols[index] = original.charAt(index);
        return new String(symbols);
    }

    protected boolean userExists(String username)
    {
        for(Handler handler : handlers)
        {
            if(handler.getUsername() == null) continue;
            if(handler.getUsername().equals(username)) return true;
        }
        return false;
    }

    protected void respond(String message)
    {
        try
        {
            synchronized (out) {
                out.writeUTF(message);
            }
            out.flush();
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }

    protected void toAll(String message)
    {
        broadcast(message);
        respond(message);
    }

    protected void broadcast (String message) {
        synchronized (handlers) {
            Enumeration<Handler> e = handlers.elements ();
            while (e.hasMoreElements ()) {
                Handler handler = e.nextElement ();
                if(handler == this) continue;
                try {
                    synchronized (handler.out) {
                        handler.out.writeUTF(message);
                    }
                    handler.out.flush ();
                } catch (IOException ex) {
                    handler.stop ();
                }
            }
        }
    }

    protected boolean kick(String content)
    {
        if(content.contains("\\kick "))
        {
            Matcher matchKick = Pattern.compile(
                    "\\\\kick\\s([\\p{Graph}\\p{Blank}]+)").matcher(content);
            if(matchKick.find())
            {
                String toRemove = matchKick.group(1);
                if (!admin)
                {
                    respond("#MESSAGE#Hehe, you are not admin for kicking :D");
                    return false;
                }
                synchronized(handlers)
                {
                    for(Iterator<Handler> iter = handlers.iterator(); iter.hasNext();)
                    {
                        Handler h = iter.next();
                        if(h.username.equals(toRemove))
                        {
                            h.respond("#MESSAGE#You have been kicked by " + this.username);
                            h.stop();
                            iter.remove();
                            broadcast("#RMUSER#" + toRemove); //tell other users that this user left chat
                            try {
                                h.conn.close ();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
            else
            {
                respond("#MESSAGE#You didn't provide user.");
                return false;
            }
            return true;
        }
        return false;
    }

    public static String getActiveUsers()
    {
        String result = "";
        for(Handler h : handlers)
        {
            result += h.getUsername() + ";";
        }
        if(handlers.size() < 1) return "";
        return (String) result.subSequence(0, result.length() - 1);
    }

    public String getUsername()
    {
        return username;
    }
}
