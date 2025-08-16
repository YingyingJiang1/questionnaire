    @Override
    public void draw(CommandProcess process, JvmModel result) {
        TableElement table = new TableElement(2, 5).leftCellPadding(1).rightCellPadding(1);

        for (Map.Entry<String, List<JvmItemVO>> entry : result.getJvmInfo().entrySet()) {
            String group = entry.getKey();
            List<JvmItemVO> items = entry.getValue();

            table.row(true, label(group).style(Decoration.bold.bold()));
            for (JvmItemVO item : items) {
                String valueStr;
                if (item.getValue() instanceof Map && item.getName().endsWith("MEMORY-USAGE")) {
                    valueStr = renderMemoryUsage((Map<String, Object>) item.getValue());
                } else {
                    valueStr = renderItemValue(item.getValue());
                }
                if (item.getDesc() != null) {
                    table.row(item.getName() + "\n[" + item.getDesc() + "]", valueStr);
                } else {
                    table.row(item.getName(), valueStr);
                }
            }
            table.row("", "");
        }

        process.write(RenderUtil.render(table, process.width()));
    }
