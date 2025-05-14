package com.capstone.gradify.Service.spreadsheet;

import com.capstone.gradify.Entity.records.ClassSpreadsheet;
import com.capstone.gradify.Entity.user.TeacherEntity;
import com.capstone.gradify.Service.spreadsheet.CloudSpreadsheetInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Manager service for cloud spreadsheet integrations.
 * This service handles accessing and processing spreadsheet data from cloud providers.
 */
@Service
public class CloudSpreadsheetManager {

    private final List<CloudSpreadsheetInterface> cloudSpreadsheetServices;

    @Autowired
    public CloudSpreadsheetManager(List<CloudSpreadsheetInterface> cloudSpreadsheetServices) {
        this.cloudSpreadsheetServices = cloudSpreadsheetServices;
    }

    /**
     * Process a spreadsheet from a shared cloud link
     *
     * @param sharedLink The URL or shared link to the spreadsheet
     * @param teacher    The teacher entity who is uploading the spreadsheet
     * @return The processed ClassSpreadsheet entity
     * @throws IOException              If there's an issue accessing the spreadsheet
     * @throws GeneralSecurityException If there's a security issue with credentials
     * @throws IllegalArgumentException If no service can process the provided link
     */
    public ClassSpreadsheet processSharedSpreadsheet(String sharedLink, TeacherEntity teacher)
            throws IOException, GeneralSecurityException {

        // Find the appropriate service based on the link
        for (CloudSpreadsheetInterface service : cloudSpreadsheetServices) {
            if (service.canProcessLink(sharedLink)) {
                return service.processSharedSpreadsheet(sharedLink, teacher);
            }
        }

        throw new IllegalArgumentException("Unsupported cloud spreadsheet link: " + sharedLink);
    }

    /**
     * Check if this system can process the given link
     *
     * @param link The URL or shared link to check
     * @return true if any service can process this link, false otherwise
     */
    public boolean canProcessLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            return false;
        }

        for (CloudSpreadsheetInterface service : cloudSpreadsheetServices) {
            if (service.canProcessLink(link)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the name of the cloud service that can process this link
     *
     * @param link The URL or shared link to check
     * @return Name of the service, or "Unknown" if no service can process it
     */
    public String getServiceNameForLink(String link) {
        if (link == null || link.trim().isEmpty()) {
            return "Unknown";
        }

        for (CloudSpreadsheetInterface service : cloudSpreadsheetServices) {
            if (service.canProcessLink(link)) {
                // Extract service name from class name
                String className = service.getClass().getSimpleName();
                if (className.endsWith("Service")) {
                    return className.substring(0, className.length() - 7);
                }
                return className;
            }
        }
        return "Unknown";
    }
}