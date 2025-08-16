    private void drawMBeanAttributes(CommandProcess process, Map<String, List<MBeanAttributeVO>> mbeanAttributeMap) {
        for (Map.Entry<String, List<MBeanAttributeVO>> entry : mbeanAttributeMap.entrySet()) {
            String objectName = entry.getKey();
            List<MBeanAttributeVO> attributeVOList = entry.getValue();

            TableElement table = new TableElement().leftCellPadding(1).rightCellPadding(1);
            table.row(true, "OBJECT_NAME", objectName);
            table.row(true, label("NAME").style(Decoration.bold.bold()),
                    label("VALUE").style(Decoration.bold.bold()));

            for (MBeanAttributeVO attributeVO : attributeVOList) {
                String attributeName = attributeVO.getName();
                String valueStr;
                if (attributeVO.getError() != null) {
                    valueStr = RenderUtil.render(new LabelElement(attributeVO.getError()).style(Decoration.bold_off.fg(Color.red)));
                } else {
                    //convert array to list
                    // TODO support all array type
                    Object value = attributeVO.getValue();
                    if (value instanceof String[]) {
                        value = Arrays.asList((String[]) value);
                    } else if (value instanceof Integer[]) {
                        value = Arrays.asList((Integer[]) value);
                    } else if (value instanceof Long[]) {
                        value = Arrays.asList((Long[]) value);
                    } else if (value instanceof int[]) {
                        value = convertArrayToList((int[]) value);
                    } else if (value instanceof long[]) {
                        value = convertArrayToList((long[]) value);
                    }
                    //to string
                    valueStr = String.valueOf(value);
                }
                table.row(attributeName, valueStr);
            }
            process.write(RenderUtil.render(table, process.width()));
            process.write("\n");
        }
    }
