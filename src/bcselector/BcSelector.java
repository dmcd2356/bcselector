/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bcselector;

import gui.GuiControls;
import gui.GuiControls.Orient;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.filechooser.FileNameExtensionFilter;
import util.Utils;

/**
 *
 * @author dan
 */
public class BcSelector {

  private static GuiControls     mainFrame;
  private static JFileChooser    fileSelector;
  private static JComboBox       classCombo;
  private static JComboBox       methodCombo;
  private static File            jarFile;
  private static ArrayList<String> fullMethList;
  private static ArrayList<String> classList;
  private static HashMap<String, ArrayList<String>> clsMethMap; // maps the list of methods to each class
  private static HashMap<String, JMenuItem> menuItems;
  private static ServerInterface serverConnection;
  private static int             serverPort = 6000;
  
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // start the gui panel
    BcSelector gui = new BcSelector();
  }

  private BcSelector() {
    fullMethList = new ArrayList<>();
    classList = new ArrayList<>();
    clsMethMap = new HashMap<>();
    fileSelector = new JFileChooser();
    menuItems = new HashMap<>();

    // create an interface to send commands to the solver
    serverConnection= new ServerInterface(serverPort);
    if (!serverConnection.isValid()) {
      Utils.printStatusError("server port failure on " + serverPort + ". Make sure bcextractor is running.");
    } else {
      Utils.printStatusMessage("Connected to Solver on port " + serverPort);
    }
    
    // create the main panel and controls
    mainFrame = new GuiControls();
    createMainPanel();
  }
  
  /**
   * The Main Panel
   */
  private void createMainPanel() {
    // if a panel already exists, close the old one
    if (mainFrame.isValidFrame()) {
      Utils.printStatusInfo("Closing previous Main Frame that was still running");
      mainFrame.close();
    }

    GuiControls gui = mainFrame;

    // create the frame
    JFrame frame = gui.newFrame("danlauncher", 600, 250, GuiControls.FrameSize.NOLIMIT);
    frame.addWindowListener(new Window_MainListener());

    String panel = null; // this creates the entries in the main frame
//    gui.makeButton    (panel, "BTN_LOADJAR"  , Orient.LEFT, true , "Load");
//    gui.makeLineGap   (panel, 20);
    gui.makeLabel     (panel, "LBL_CLASS"    , Orient.LEFT, false, "Class");
    gui.makeCombobox  (panel, "COMBO_CLASS"  , Orient.LEFT, true);
    gui.makeLabel     (panel, "LBL_METHOD"   , Orient.LEFT, false, "Method");
    gui.makeCombobox  (panel, "COMBO_METHOD" , Orient.LEFT, true);
    gui.makeLineGap   (panel, 20);
    gui.makeButton    (panel, "BTN_REQUEST"  , Orient.LEFT, true , "Request");

    JMenuBar launcherMenuBar = new JMenuBar();
    mainFrame.getFrame().setJMenuBar(launcherMenuBar);
    JMenu menu = launcherMenuBar.add(new JMenu("Load"));
    addMenuItem (menu, "MENU_SEL_JAR"    , "Load Jar file", new Action_SelectJarFile());
    
    classCombo  = gui.getCombobox ("COMBO_CLASS");
    methodCombo = gui.getCombobox ("COMBO_METHOD");
    enableControlSelections(false);
    
    // setup the handlers for the controls
//    gui.getButton("BTN_LOADJAR").addActionListener(new Action_SelectJarFile());
    gui.getButton("BTN_REQUEST").addActionListener(new Action_RequestBytecode());
    gui.getCombobox("COMBO_CLASS").addActionListener(new Action_ClassSelect());
    gui.getCombobox("COMBO_METHOD").addActionListener(new Action_MethodSelect());

    // display the frame
    gui.display();
  }

  private static void addMenuItem(JMenu menucat, String id, String title, ActionListener action) {
    if (menuItems.containsKey(id)) {
      Utils.printStatusError("Menu Item '" + id + "' already defined!");
      return;
    }
    JMenuItem item = new JMenuItem(title);
    item.addActionListener(action);
    menucat.add(item);
    menuItems.put(id, item);
  }

  private class Window_MainListener extends java.awt.event.WindowAdapter {
    @Override
    public void windowClosing(java.awt.event.WindowEvent evt) {
      Utils.printStatusInfo("ACTION - EXITING");
      
      // else we can close the frame and exit
      Utils.printStatusInfo("Closing Main Frame and exiting");
      mainFrame.close();
      System.exit(0);
    }
  }

  private class Action_SelectJarFile implements ActionListener{
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      FileNameExtensionFilter filter = new FileNameExtensionFilter("Jar Files", "jar");
      fileSelector.setFileFilter(filter);
      fileSelector.setMultiSelectionEnabled(false);
      fileSelector.setApproveButtonText("Load");
      int retVal = fileSelector.showOpenDialog(mainFrame.getFrame());
      if (retVal != JFileChooser.APPROVE_OPTION) {
        return;
      }

      // read the file
      jarFile = fileSelector.getSelectedFile();
      Utils.printStatusInfo("ACTION - LOAD JAR FILE: " + jarFile.getAbsolutePath());
      //String projectName = file.getName();
      String pathname = jarFile.getParentFile().getAbsolutePath() + "/";
      
      // init the class and method selections
      clsMethMap = new HashMap<>();
      classList = new ArrayList<>();
      fullMethList = new ArrayList<>();

      // read the list of methods from the "methodlist.txt" file created by Instrumentor
      try {
        File file = new File(pathname + "methodlist.txt");
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          fullMethList.add(line);
        }
        fileReader.close();
      } catch (IOException ex) {
        Utils.printStatusError("setupClassList: " + ex);
        enableControlSelections(false);
        return;
      }

      // exit if no methods were found
      if (fullMethList.isEmpty()) {
        Utils.printStatusError("setupClassList: No methods found in methodlist.txt file!");
        enableControlSelections(false);
        return;
      }
        
      // now map the entries from the method list to the corresponding class name
      Collections.sort(fullMethList);
      String curClass = "";
      ArrayList<String> methList = new ArrayList<>();
      for (String fullMethodName : fullMethList) {
        int methOffset = fullMethodName.indexOf(".");
        if (methOffset > 0) {
          String className = fullMethodName.substring(0, methOffset);
          String methName = fullMethodName.substring(methOffset + 1);
          // if new class was found and a method list was valid, save the class and classMap
          if (!curClass.equals(className) && !methList.isEmpty()) {
            classList.add(curClass);
            clsMethMap.put(curClass, methList);
            methList = new ArrayList<>();
          }

          // save the class name for the list and add the method name to it
          curClass = className;
          methList.add(methName);
        }
      }

      // save the remaining class
      if (!methList.isEmpty()) {
        classList.add(curClass);
        clsMethMap.put(curClass, methList);
      }

      // setup the class and method selections
      setClassSelections();
      Utils.printStatusInfo(classList.size() + " classes and " + fullMethList.size() + " methods found");
      enableControlSelections(true);
      
      // tell server which jar file is selected
      serverConnection.sendMessage("jar: " + jarFile.getAbsolutePath());
    }
  }

  private class Action_ClassSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      JComboBox cbSelect = (JComboBox) evt.getSource();
      String classSelect = (String) cbSelect.getSelectedItem();
      setMethodSelections(classSelect);
    }
  }

  private class Action_MethodSelect implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
    }
  }

  private class Action_RequestBytecode implements ActionListener {
    @Override
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      // tell the server we want to generate bytecode now
      serverConnection.sendMessage("class: " + classCombo.getSelectedItem().toString());
      serverConnection.sendMessage("method: " + methodCombo.getSelectedItem().toString());
      serverConnection.sendMessage("get_bytecode");
    }
  }

  private void enableControlSelections(boolean enable) {
    mainFrame.getButton("BTN_REQUEST").setEnabled(enable);
    mainFrame.getCombobox ("COMBO_CLASS").setEnabled(enable);
    mainFrame.getCombobox ("COMBO_METHOD").setEnabled(enable);
    mainFrame.getLabel("LBL_CLASS").setEnabled(enable);
    mainFrame.getLabel("LBL_METHOD").setEnabled(enable);
  }  

  private static void setClassSelections() {
    classCombo.removeAllItems();
    for (int ix = 0; ix < classList.size(); ix++) {
      String cls = classList.get(ix);
      classCombo.addItem(cls);
    }

    // init class selection to 1st item
    classCombo.setSelectedIndex(0);
    
    // now update the method selections
    setMethodSelections((String) classCombo.getSelectedItem());
  }

  private static void setMethodSelections(String clsname) {
    // init the method selection list
    methodCombo.removeAllItems();

    // make sure we have a valid class selection
    if (clsname == null || clsname.isEmpty()) {
      return;
    }
    
    if (clsname.endsWith(".class")) {
      clsname = clsname.substring(0, clsname.length()-".class".length());
    }

    // get the list of methods for the selected class from the hash map
    ArrayList<String> methodSelection = clsMethMap.get(clsname);
    if (methodSelection != null) {
      // now get the methods for the class and place in the method selection combobox
      for (String method : methodSelection) {
        methodCombo.addItem(method);
      }

      // set the 1st entry as the default selection
      methodCombo.setSelectedIndex(0);
    }
  }
  
}
