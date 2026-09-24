package io.meterian.aicalendar.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps each draft as .eml files in the drafts folder, one file per attendee, so a mail program can show them.
 * The files are named after the draft, for example D-1-john_example.com.eml.
 */
public class DraftFolder {

    private final Path folder;
    private final EmlFormatter formatter;
    private final Clock clock;

    public DraftFolder(Path folder, EmlFormatter formatter, Clock clock) {
        this.folder = folder;
        this.formatter = formatter;
        this.clock = clock;
    }

    /** Replaces the files of this draft with the given emails. Returns the paths of the new files. */
    public List<String> writeDraft(String draftId, List<Email> emails) {
        deleteDraft(draftId);
        List<String> writtenFiles = new ArrayList<>();
        try {
            Files.createDirectories(folder);
            for (Email email : emails) {
                Path file = folder.resolve(
                        draftId + "-" + EmailFileNames.convertAddressToFileNamePart(email.to.address) + ".eml");
                String eml = formatter.formatEmail(email, ZonedDateTime.now(clock));
                Files.writeString(file, eml, StandardCharsets.UTF_8);
                writtenFiles.add(file.toString());
            }
            return writtenFiles;
        } catch (IOException e) {
            throw new EmailException("Could not write draft " + draftId + " to " + folder + ": " + e.getMessage(), e);
        }
    }

    public void deleteDraft(String draftId) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try {
            for (Path file : listDraftFiles(draftId)) {
                Files.delete(file);
            }
        } catch (IOException e) {
            throw new EmailException("Could not delete draft " + draftId + " in " + folder + ": " + e.getMessage(), e);
        }
    }

    /** "D-1-" matches the files of D-1 only, not the files of D-10. */
    private List<Path> listDraftFiles(String draftId) throws IOException {
        String fileNamePrefix = draftId + "-";
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().startsWith(fileNamePrefix))
                    .collect(Collectors.toList());
        }
    }
}
