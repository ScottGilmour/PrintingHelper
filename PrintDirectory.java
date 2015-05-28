
import javax.print.PrintService;
import java.io.File;
import java.util.Arrays;

/**
 * Created by Scott on 5/20/2015.
 */
public class PrintDirectory {
    PrintService pServ;
    File directory;
    String[] includedWords;
    Boolean active;

    public PrintDirectory(PrintService pServ, File directory, String[] includedWords) {
        this.pServ = pServ;
        this.directory = directory;
        this.includedWords = includedWords;
        this.active = false;
    }

    public String[] getIncludedWords() {
        return includedWords;
    }

    public void setIncludedWords(String[] includedWords) {
        this.includedWords = includedWords;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public File getDirectory() {
        return directory;
    }

    @Override
    public String toString() {
        return "Printer: " + pServ +
                ", directory=" + directory +
                ", includedWords=" + Arrays.toString(includedWords) +
                ", active=" + active;
    }
}

