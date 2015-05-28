import com.thoughtworks.xstream.XStream;
import javax.print.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PrinterException;
import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Scott on 5/20/2015.
 * Multi threaded printing helper
 * Sends specific PDF documents to user specified printers based on a word filter
 */
public class GuiForm {
    private JPanel panel1;
    private JComboBox printerDropDown;
    private JTextField directoryText;
    private JButton setButton;
    private JList directoryList;
    private JButton startButton;
    private JButton shutdownButton;
    private JTextField includedWords;
    private JButton addButton;
    private List<PrintDirectory> directories;
    private XStream xStream = new XStream();
    private List<String> excludedNames = new ArrayList<String>();
    private ScheduledExecutorService scheduledExecutorService;

    public GuiForm() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        directories = new ArrayList<PrintDirectory>();
        loadDirectories();
        loadExcludedFiles();
        scheduledExecutorService = Executors.newScheduledThreadPool(1);

        for (PrintService printer : printServices) {
            printerDropDown.addItem(printer);
        }

        setButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JFileChooser fc = new JFileChooser();

                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnVal = fc.showOpenDialog(panel1);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    directoryText.setText(file.getAbsolutePath());
                }
            }
        });

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PrintDirectory printDirectory = (PrintDirectory) directoryList.getSelectedValue();

                printDirectory.setActive(true);
                directoryList.setSelectedValue(printDirectory, false);

                CreateThread(printDirectory);
            }
        });

        shutdownButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!scheduledExecutorService.isTerminated())
                    scheduledExecutorService.shutdown();

                PrintDirectory printDirectory = (PrintDirectory) directoryList.getSelectedValue();

                printDirectory.setActive(false);
                directoryList.setSelectedValue(printDirectory, false);
            }
        });

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String[] includeWords = includedWords.getText().split(",");
                PrintDirectory printDirectory =  new PrintDirectory((PrintService) printerDropDown.getSelectedItem(), new File(directoryText.getText()), includeWords);

                try {
                    saveDirectories(printDirectory);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    private void CreateThread(PrintDirectory printDirectory) {
        PrintingThread printThread = new PrintingThread(printDirectory.pServ, printDirectory.getDirectory(), printDirectory.getIncludedWords());
        if (!scheduledExecutorService.isTerminated() || !scheduledExecutorService.isShutdown())
            scheduledExecutorService.scheduleAtFixedRate(printThread, 0, 2, TimeUnit.SECONDS);
        else
            System.out.println("Executor service is currently terminated.");
    }

    public class PrintingThread implements Runnable {
        private FilenameFilter filter;
        private PrintService p_serv;
        private String directory;
        private String[] included;

        public PrintingThread(PrintService printService, File dir, final String[] includedWords) {
            included = includedWords;
            directory = dir.getAbsolutePath();
            p_serv = printService;
            filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (!name.toLowerCase().endsWith(".pdf"))
                        return false;

                    if (excludedNames.contains(name))
                        return false;

                    for (String word : included) {
                        if (name.contains(word))
                            return true;
                    }

                    return false;
                }
            };
        }

        @Override
        public void run() {
            System.out.println("Scanning for files...");
            File directoryToMonitor = new File(directory);
            File[] files = directoryToMonitor.listFiles(filter);

            for (File file : files) {
                System.out.println("Printing File: " + file.getName() + " - " + file.getAbsolutePath());
                excludedNames.add(file.getName());

                try {
                    copyFile(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    PrintDocumentByName(file.getName(), p_serv, directoryToMonitor);
                } catch (PrinterException e) {
                    e.printStackTrace();
                }

                try {
                    addDocumentNameToExclusionList(file.getName());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    moveFile(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveDirectories(PrintDirectory newPDir) throws IOException {
        directories.add(newPDir);
        String xml = xStream.toXML(directories);
        File xmlFile = new File("./directories.xml");
        FileWriter fWrite = null;
        fWrite = new FileWriter(xmlFile);
        fWrite.write(xml);
        fWrite.close();
        directoryList.setListData(directories.toArray());
    }

    private void loadExcludedFiles() {
        File excludedFiles = new File("./excludedFiles.xml");

        excludedNames.addAll((Collection<? extends String>) xStream.fromXML(excludedFiles));
    }

    private void addDocumentNameToExclusionList(String name) throws IOException {
        excludedNames.add(name);
        String xml = xStream.toXML(excludedNames);

        File xmlFile = new File("./excludedFiles.xml");
        FileWriter fWrite = new FileWriter(xmlFile);
        fWrite.write(xml);
        fWrite.close();
    }

    private static void copyFile(File file) throws IOException {
        System.out.println("Copying file to: " + System.getProperty("user.dir") + "\\" + file.getName());
        File destFile = new File(System.getProperty("user.dir") + "\\" + file.getName());
        try {
            Files.copy(file.toPath(), destFile.toPath());
        } catch (FileAlreadyExistsException fe) {
            System.out.println("File already exists, ignoring");
        }
    }

    private static void moveFile(File file) throws IOException {
        File destFile = new File(file.getParent() + "/archive/" + file.getName());

        try {
            Files.move(file.toPath(), destFile.toPath());
        } catch (NoSuchFileException nsf) {
            System.out.println("File moved?");
        }
    }

    private void PrintDocumentByName(String FileName, PrintService printService, File directory) throws PrinterException {
        String printCmd = "acrowrap.exe";
        String command = "\"" +printCmd + "\" /acceptlicense /t \"" + FileName + "\" \"" + printService.getName() + "\"";

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = "";
            while ((line = reader.readLine())!= null) {
                output.append(line + "\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(output);
    }

    private void loadDirectories() {
        File directoryFile = new File("./directories.xml");

        directories.addAll((Collection<? extends PrintDirectory>) xStream.fromXML(directoryFile));
        directoryList.setListData(directories.toArray());
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Hype Auto Printing");
        frame.setContentPane(new GuiForm().panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
