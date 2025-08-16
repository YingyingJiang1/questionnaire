    private ObjectNode generatePDFSummaryData(PDDocument document) {
        ObjectNode summaryData = objectMapper.createObjectNode();

        // Check if encrypted
        if (document.isEncrypted()) {
            summaryData.put("encrypted", true);
        }

        // Check permissions
        AccessPermission ap = document.getCurrentAccessPermission();
        ArrayNode restrictedPermissions = objectMapper.createArrayNode();

        if (!ap.canAssembleDocument()) restrictedPermissions.add("document assembly");
        if (!ap.canExtractContent()) restrictedPermissions.add("content extraction");
        if (!ap.canExtractForAccessibility()) restrictedPermissions.add("accessibility extraction");
        if (!ap.canFillInForm()) restrictedPermissions.add("form filling");
        if (!ap.canModify()) restrictedPermissions.add("modification");
        if (!ap.canModifyAnnotations()) restrictedPermissions.add("annotation modification");
        if (!ap.canPrint()) restrictedPermissions.add("printing");

        if (restrictedPermissions.size() > 0) {
            summaryData.set("restrictedPermissions", restrictedPermissions);
            summaryData.put("restrictedPermissionsCount", restrictedPermissions.size());
        }

        // Check standard compliance
        if (checkForStandard(document, "PDF/A")) {
            summaryData.put("standardCompliance", "PDF/A");
            summaryData.put("standardPurpose", "long-term archiving");
        } else if (checkForStandard(document, "PDF/X")) {
            summaryData.put("standardCompliance", "PDF/X");
            summaryData.put("standardPurpose", "graphic exchange");
        } else if (checkForStandard(document, "PDF/UA")) {
            summaryData.put("standardCompliance", "PDF/UA");
            summaryData.put("standardPurpose", "universal accessibility");
        } else if (checkForStandard(document, "PDF/E")) {
            summaryData.put("standardCompliance", "PDF/E");
            summaryData.put("standardPurpose", "engineering workflows");
        } else if (checkForStandard(document, "PDF/VT")) {
            summaryData.put("standardCompliance", "PDF/VT");
            summaryData.put("standardPurpose", "variable and transactional printing");
        }

        return summaryData;
    }
