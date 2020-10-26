//ADD_JBSIS_13A_DISPOSITION_LOOKUP_ATTRIBUTES
import com.sustain.util.ContainerUtils;

attributeName = "Row";
attributeType = "JBSIS_13a";

lookupList = SysLookupList.CASE_DISPOSITION.get();
dispositionTypeMap = ContainerUtils.$m(
        900, ContainerUtils.$l("900"),
        950, ContainerUtils.$l("950"),
        1000, ContainerUtils.$l("1000SC"),
        1200, ContainerUtils.$l("1200SC"),
        1300, ContainerUtils.$l("1200C", "1300SC", "1300VSC", "2100"),//Liane Herbst requested 1300VSC via email 10/23/2020
        //1301, ContainerUtils.$l("1301SC"),Charlton made this code and tested there is no conflict between Case Voided and Case Dismissal
        1400, ContainerUtils.$l("1400SC"),
        1550, ContainerUtils.$l("1550"),
        1560, ContainerUtils.$l("1560"),
        1600, ContainerUtils.$l("1600SC"),
        1700, ContainerUtils.$l("1700SC"),
        1900, ContainerUtils.$l("1900SC"),
        2000, ContainerUtils.$l("2000SC")
);

for (entry in dispositionTypeMap.entrySet()) {
    final Integer rowNumber = entry.getKey();
    for (String lookupItemCode : entry.getValue()) {
        lookupItem = lookupList.findByCode(lookupItemCode);

        if (lookupItem == null) {
            lookupItem = new LookupItem();
            lookupItem.setCode(lookupItemCode);
            lookupItem.setLabel(lookupItemCode);
            lookupItem.setLookupList(lookupList);
            lookupList.getItems().add(lookupItem);
        }

        lookupAttribute = new LookupAttribute();
        lookupAttribute.setName(attributeName);
        lookupAttribute.setValue(String.valueOf(rowNumber));
        lookupAttribute.setAttributeType(attributeType);
        lookupAttribute.setLookupItem(lookupItem);
        lookupItem.getAttributes().add(lookupAttribute);

        logger.debug('adding ' + attributeType + ' ' + attributeName + ' ' + rowNumber + ' attribute to ' + lookupList.name + ':' + lookupItemCode);
    }
}

lookupList.saveOrUpdate();




























