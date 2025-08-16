
public static FieldVO[] getFields(Class clazz, Integer expand) {
    return Arrays.stream(clazz.getDeclaredFields())
                 .map(field -> {
                     FieldVO fieldVO = new FieldVO();
                     fieldVO.setName(field.getName())
                            .setType(StringUtils.classname(field.getType()))
                            .setModifier(StringUtils.modifier(field.getModifiers(), ','))
                            .setAnnotations(getAnnotations(field.getAnnotations()));
                     
                     if (Modifier.isStatic(field.getModifiers())) {
                         fieldVO.setStatic(true)
                                .setValue(new ObjectVO(getFieldValue(field), expand));
                     } else {
                         fieldVO.setStatic(false);
                     }
                     
                     return fieldVO;
                 })
                 .toArray(FieldVO[]::new);
}
