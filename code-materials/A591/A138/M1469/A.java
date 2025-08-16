    public static FieldVO[] getFields(Class clazz, Integer expand) {
        Field[] fields = clazz.getDeclaredFields();
        if (fields.length == 0) {
            return new FieldVO[0];
        }

        List<FieldVO> list = new ArrayList<FieldVO>(fields.length);
        for (Field field : fields) {
            FieldVO fieldVO = new FieldVO();
            fieldVO.setName(field.getName());
            fieldVO.setType(StringUtils.classname(field.getType()));
            fieldVO.setModifier(StringUtils.modifier(field.getModifiers(), ','));
            fieldVO.setAnnotations(getAnnotations(field.getAnnotations()));
            if (Modifier.isStatic(field.getModifiers())) {
                fieldVO.setStatic(true);
                fieldVO.setValue(new ObjectVO(getFieldValue(field), expand));
            } else {
                fieldVO.setStatic(false);
            }
            list.add(fieldVO);
        }
        return list.toArray(new FieldVO[0]);
    }
