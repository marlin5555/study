package xu.tools;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EtchedBorder;

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.IntellitypeListener;
import com.melloware.jintellitype.JIntellitype;

/**
 * Created by xu on 2016/8/12.
 */
public class LatexClip extends JFrame implements HotkeyListener, IntellitypeListener {
    private static LatexClip mainFrame;
    private static final int ALT_SHIFT_B = 89;
    private static final int CTRL_SHIFT_C = 90;
    private final JButton btnRegisterHotKey = new JButton();
    private final JButton btnUnregisterHotKey = new JButton();
    private final JPanel bottomPanel = new JPanel();
    private final JPanel mainPanel = new JPanel();
    private final JPanel topPanel = new JPanel();
    private final JScrollPane scrollPane = new JScrollPane();
    private final JTextArea textArea = new JTextArea();
    SystemClipboardMonitor tmp = new SystemClipboardMonitor();

    public LatexClip(){
        initComponents();
    }

    private void initComponents() {
        mainPanel.setLayout(new BorderLayout());
        topPanel.setBorder(new EtchedBorder(1));
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.setBorder(new EtchedBorder(1));
        btnRegisterHotKey.setText("RegisterHotKey");
        btnRegisterHotKey.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                btnRegisterHotKey_actionPerformed(e);
            }
        });
        btnUnregisterHotKey.setText("UnregisterHotKey");
        btnUnregisterHotKey.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                btnUnregisterHotKey_actionPerformed(e);
            }
        });
        topPanel.add(btnRegisterHotKey);
        topPanel.add(btnUnregisterHotKey);
        scrollPane.getViewport().add(textArea);
        bottomPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(bottomPanel, BorderLayout.CENTER);

        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent evt) {
                // don't forget to clean up any resources before close
                JIntellitype.getInstance().cleanUp();
                System.exit(0);
            }
        });

        this.getContentPane().add(mainPanel);
        this.pack();
        this.setSize(800, 600);
    }


    private void btnRegisterHotKey_actionPerformed(ActionEvent aEvent) {
        // assign the WINDOWS+A key to the unique id 88 for identification
        JIntellitype.getInstance().registerHotKey(ALT_SHIFT_B, JIntellitype.MOD_ALT + JIntellitype.MOD_SHIFT, 'B');
        JIntellitype.getInstance().registerSwingHotKey(CTRL_SHIFT_C, Event.CTRL_MASK + Event.SHIFT_MASK, 'C');

        // use a 0 for the modifier if you just want a single keystroke to be a
        // hotkey
        // clear the text area
        textArea.setText("");
        output("RegisterHotKey ALT+SHIFT+B was assigned uniqueID 89");
        output("RegisterHotKey CTRL+SHIFT+C was assigned uniqueID 90");
    }
    private void btnUnregisterHotKey_actionPerformed(ActionEvent aEvent) {
        JIntellitype.getInstance().unregisterHotKey(ALT_SHIFT_B);
        JIntellitype.getInstance().unregisterHotKey(CTRL_SHIFT_C);
        output("UnregisterHotKey ALT+SHIFT+B");
        output("UnregisterHotKey CTRL+SHIFT+C");
        output("Press WINDOWS+A or ALT+SHIFT+B in another application and you will NOT see the debug output in the textarea.");
    }

    private void output(String text) {
        textArea.append(text);
        textArea.append("\n");
    }

    public static void main(String[] args) {
        System.out.println(new File(".").getAbsolutePath());
        // first check to see if an instance of this application is already
        // running, use the name of the window title of this JFrame for checking
        if (JIntellitype.checkInstanceAlreadyRunning("JIntellitype Test Application")) {
            System.exit(1);
        }

        // next check to make sure JIntellitype DLL can be found and we are on
        // a Windows operating System
        if (!JIntellitype.isJIntellitypeSupported()) {
            System.exit(1);
        }

        mainFrame = new LatexClip();
        mainFrame.setTitle("JIntellitype Test Application");
        center(mainFrame);
        mainFrame.setVisible(true);
        mainFrame.initJIntellitype();

    }
    public void initJIntellitype() {
        try {

            // initialize JIntellitype with the frame so all windows commands can
            // be attached to this window
            JIntellitype.getInstance().addHotKeyListener(this);
            JIntellitype.getInstance().addIntellitypeListener(this);
            output("JIntellitype initialized");
        } catch (RuntimeException ex) {
            output("Either you are not on Windows, or there is a problem with the JIntellitype library!");
        }
    }
    private static void center(JFrame aFrame) {
        final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final Point centerPoint = ge.getCenterPoint();
        final Rectangle bounds = ge.getMaximumWindowBounds();
        final int w = Math.min(aFrame.getWidth(), bounds.width);
        final int h = Math.min(aFrame.getHeight(), bounds.height);
        final int x = centerPoint.x - (w / 2);
        final int y = centerPoint.y - (h / 2);
        aFrame.setBounds(x, y, w, h);
        if ((w == bounds.width) && (h == bounds.height)) {
            aFrame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
        aFrame.validate();
    }

    public void onHotKey(int aIdentifier) {
        output("WM_HOTKEY message received " + Integer.toString(aIdentifier));
        if(aIdentifier==ALT_SHIFT_B)
            tmp.begin(); //开始监视
        else if(aIdentifier ==CTRL_SHIFT_C){
            tmp.stop();
        }
    }

    public void onIntellitype(int aCommand) {

        switch (aCommand) {
            case JIntellitype.APPCOMMAND_BROWSER_BACKWARD:
                output("BROWSER_BACKWARD message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_BROWSER_FAVOURITES:
                output("BROWSER_FAVOURITES message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_BROWSER_FORWARD:
                output("BROWSER_FORWARD message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_BROWSER_HOME:
                output("BROWSER_HOME message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_BROWSER_REFRESH:
                output("BROWSER_REFRESH message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_BROWSER_SEARCH:
                output("BROWSER_SEARCH message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_BROWSER_STOP:
                output("BROWSER_STOP message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_LAUNCH_APP1:
                output("LAUNCH_APP1 message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_LAUNCH_APP2:
                output("LAUNCH_APP2 message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_LAUNCH_MAIL:
                output("LAUNCH_MAIL message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_MEDIA_NEXTTRACK:
                output("MEDIA_NEXTTRACK message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_MEDIA_PLAY_PAUSE:
                output("MEDIA_PLAY_PAUSE message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_MEDIA_PREVIOUSTRACK:
                output("MEDIA_PREVIOUSTRACK message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_MEDIA_STOP:
                output("MEDIA_STOP message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_VOLUME_DOWN:
                output("VOLUME_DOWN message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_VOLUME_UP:
                output("VOLUME_UP message received " + Integer.toString(aCommand));
                break;
            case JIntellitype.APPCOMMAND_VOLUME_MUTE:
                output("VOLUME_MUTE message received " + Integer.toString(aCommand));
                break;
            default:
                output("Undefined INTELLITYPE message caught " + Integer.toString(aCommand));
                break;
        }
    }

}
