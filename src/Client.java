import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client extends JFrame implements Runnable, ActionListener, ListSelectionListener
{
    protected JList users;
    protected DefaultListModel<String> usersListModel;
    protected JButton privateButton;

    protected boolean admin = false;
    private Client thisObject = this;
    protected Thread listener;
    protected DataInputStream in;
    protected DataOutputStream out;
    protected JTextArea output;
    protected JTextField input;

    public static void main(String args[]) throws IOException
    {
//        if(args.length != 2)
//            throw new RuntimeException("Pass address and port as arguments.");
        Socket socket = new Socket("127.0.0.1", 80);
        Client cl = new Client("Messanger", socket.getInputStream(), socket.getOutputStream());
    }

    Client(String title, InputStream is, OutputStream os)
    {
        super(title);
        in = new DataInputStream(new BufferedInputStream(is));
        out = new DataOutputStream(new BufferedOutputStream(os));
        String nick;
        try
        {
            while(true)
            {
                nick = JOptionPane.showInputDialog("Input your nickname");
                out.writeUTF("#JOIN#" + nick);
                out.flush();
                String response = in.readUTF();
                System.out.println("Response: " + response);
                if(response.equals("#ERROR#USEREXISTS")) JOptionPane.showMessageDialog(null, "Such nickname exists.");
                else if(response.equals("#SUCCESSLOGIN#")) break;
            }
//            out.writeUTF("#MODE#PUBLIC");
//            out.flush();
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
            try
            {
                in.close();
                out.close();
            }
            catch(IOException ex1)
            {
                ex1.printStackTrace();
            }
            return;
        }
        super.setTitle(title + " (you are " + nick + ")");
        setLayout(new BorderLayout(10, 10));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        add("Center", new JScrollPane(output = new JTextArea()));
        output.setEditable(false);
        add("South", input = new JTextField());
        users = new JList(usersListModel = new DefaultListModel());
        privateButton = new JButton("Private");
        privateButton.addActionListener(this);
        JPanel privateBlock = new JPanel(new BorderLayout());
        JScrollPane scrollUsers = new JScrollPane(users);
        scrollUsers.setPreferredSize(new Dimension(scrollUsers.getWidth(), 100));
        privateBlock.add("North", scrollUsers);
        privateBlock.add("South", privateButton);
        add("East", privateBlock);
        users.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        users.addListSelectionListener(this);
        input.addActionListener(thisObject);
        setSize(500, 500);
//        setResizable(false);
        setVisible(true);
        input.requestFocus();
        listener = new Thread(this);
        listener.start();
    }

    @Override
    public void run()
    {
        try
        {
            loop:
            while(true)
            {
                String response = in.readUTF();
                Matcher m = Pattern.compile("#(\\p{Lu}+)#(\\p{all}*)").matcher(response);
                String type = null, content = null;//, error = null;
                System.out.println("Response: " + response);
                if(m.find())
                {
                    type = m.group(1);
                    content = m.group(2);
                }
                if(type == null)
                {
                    System.out.println("Response type is not set. Original response: " + response);
                }
                else
                {
                    switch(type)
                    {
                        case "UNDEFINED":
                            System.out.println("Response type is undefined.");
                            break loop;
                        case "MESSAGE":
                            writeToOutput(content);
                            break;
                        case "YOUADMIN":
                            admin = true;
                            writeToOutput("#You are admin now.\r\n" +
                                    "#Use '\\kick *user*' to remove people from chat.\r\n" +
                                    "#Use '\\gamestart *user*' to start a game and let user choose a word.");
                            break;
                        case "ACTIVEUSERS":
                            usersListModel.clear();
                            for(String s : content.split(";")) //write all active users to list
                            {
                                usersListModel.addElement(s);
                            }
                            break;
                        case "ADDUSER":
                            writeToOutput("#User " + content + " joined the chat.");
                            usersListModel.addElement(content);
                            break;
                        case "RMUSER":
                            writeToOutput("#User " + content + " left the chat.");
                            usersListModel.removeElement(content);
                            break;
                        case "GAMESTART":
                            writeToOutput("#Game starts!!!");
                            break;
                        case "WAKEUP":
                            out.writeUTF("#WAKEUP#");
                            out.flush();
                            break;
                        default:
                            System.out.println("Unpredicted response " + response);
                            break;
                    }
                }
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            listener = null;
            input.setVisible(false);
            validate();
            try
            {
                out.close();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    private void writeToOutput(String text)
    {
        output.append(text + "\r\n");
        output.setCaretPosition(output.getDocument().getLength());
    }


    @Override
    protected void processEvent(AWTEvent e)
    {
        if((e.getSource() == this) && (e.getID() == Event.WINDOW_DESTROY))
        {
            try
            {
                out.writeUTF("#LEFT#");
                out.flush();
            }
            catch (IOException ex1)
            {
                ex1.printStackTrace();
            }
            if (listener != null)
            {
                listener.stop ();
            }
            setVisible(false);
        }
        super.processEvent(e);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if((e.getSource() == input) && (e.getID() == Event.ACTION_EVENT))
        {
            try
            {
                out.writeUTF("#MESSAGE#" + e.getActionCommand());
                out.flush();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                listener.stop();
            }
            input.setText("");
        }
//
//        if((e.getSource() == privateButton) && (e.getID() == Event.MOUSE_DOWN))
//        {
//            System.out.println();
//            try
//            {
//                out.writeUTF("#PRIVATECONNECT#" + users.getSelectedValue());
//                out.flush();
//            }
//            catch (IOException ex)
//            {
//                ex.printStackTrace();
//                listener.stop();
//            }
//            input.setText("");
//        }
    }



    @Override
    public void valueChanged(ListSelectionEvent e)
    {
//        if (!e.getValueIsAdjusting()) {
//
//            if (users.getSelectedIndex() == -1) {
//                //No selection, disable fire button.
//                privateButton.setEnabled(false);
//
//            } else {
//                //Selection, enable the fire button.
//                privateButton.setEnabled(true);
//            }
//        }
    }
}
