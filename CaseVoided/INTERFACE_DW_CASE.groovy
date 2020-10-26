//INTERFACE_DW_CASE
if (_case.caseNumber == 'JCCP4674_001') {
  return;
}

_index = "${_index}";
division = null;
if (_case.caseType == 'SC') {
  division = '%S';
} else if (_case.caseType == 'LC') {
  if (_case.filingType == 'LEG' && _case.legacySource == 'L') {
    division = '%L';
  } else {
    division = '%C';
  }
} else {
  division = 'CV';
}

dept = _case.collect("assignments[dateRemoved == null]").first;
district = dept?.parentLocationCode;
caseNumber = _case.caseNumber;
if (_case.filingType == 'LEG') {
  def unlimitedCaseType = (!_case.la_originalCaseType && _case.caseType == 'CU') || (_case.la_originalCaseType == 'CU');  
  if (!unlimitedCaseType) {
    // case number ALH00SM1113 for legacy case number is 00SM1113
    district = caseNumber.substring(0,3);
    caseNumber = caseNumber.substring(3);
  } else {
    district = dept?.parentOrgUnitCode;
    switch (district) {
      case 'LA_CEN': 
      district = 'LA';  // mosk / central
      break;
      case 'LA_NWE':
      district = 'NW';  // northwest district
      break;
      case 'LA_WES':
      district = 'LA';  // West District
      break;
      case 'LA_SEA':
      district = 'SE';  // Southeast District
      break;
      case 'LA_NCE':
      district = 'NCB'; // North Central District
      break;
      case 'LA_SWE':
      district = 'SW';  // Southwest District
      break;
      case 'LA_SOU':
      district = 'SO';  // South District
      break;
      case 'LA_NVA':
      district = 'NV';  // North Valley District
      break;
      case 'LA_EAS':
      district = 'EA';  // East District
      break;
      case 'LA_NEA':
      district = 'LA';  // Northeast District
      break;
      case 'LA_NOR':
      district = 'NO';  // North District
      break;
      case 'LA_SCE':
      district = 'SC';  // South Central District
      break;
      default:
        district = 'LA';  // mosk / central
      break;
    }
  }
}

if (!district) {
  district = 'LA';
}

def sealed = _case.la_caseSecurity == 'C' || _case.isAccessLevelGreaterThanOrEqual(1) || !_case.collect('seals[effectiveNow]').empty;
def caseSecurity = sealed ? (['3101','3201','3401','3801'].contains(_case.category) ? 'C' : 'S') : '';
//IR546286 Charlton on 9/8/2020
/*if (_case.dispositionType =='2100') {
  caseSecurity = 'D';
}*/
if (_case.dispositionType =='2100' || _case.dispositionType =='1300VSC') {
  caseSecurity = 'D';
} 

// write TEMP CAS file
_tempCas = new File(_dir, "TEMP_CAS${_index}.dat");
logger.debug("write case info for " + _case + " to " + _tempCas);
_tempCas.withWriterAppend('ISO-8859-1') {
  writeCell(it,_case.id, 20); // case ID
  writeCell(it,division, 2);
  writeCell(it,caseNumber, 30);
  writeCell(it,district, 3); // district
  writeCell(it,caseSecurity, 1);
  writeCell(it,_case.caseName, 255); // case title
  writeCell(it,_case.category, 10); // case category (TEMP_TAB: 007)
  writeCell(it,DateUtil.format(_case.filingDate, "yyyy-MM-dd"), 10); // filing date
  writeCell(it,dept?.joCode, 10); // judge Code (TEMP_TAB: 022)
  writeCell(it,_case.dispositionType, 10); // disp type code (TEMP_TAB: 003)
  writeCell(it,DateUtil.format(_case.dispositionDate, "yyyy-MM-dd"), 10); // disp date
  writeCell(it,"E", 1); // source; E - ecourt
  //writeCell(it,"", 1);  // file destruction (ok)
  //writeCell(it,"", 16); // orig case number; (check with conv)
  //writeCell(it,"", 3);  // orig district; (check with conv)
  it.println();
}

// write TEMP EVT events on the case (past events)
_tempEvtFile = new File(_dir, "TEMP_EVT${_index}.dat");
logger.debug("write event info for " + _case + " to " + _tempEvtFile);
_tempEvtFile.withWriterAppend('ISO-8859-1') {
  i = 1;
  for (e in _case.collect("hearings[(accessLevel == null or accessLevel.ordinal() == 0) and startDateTime != null and (startDateTime.before(#p1) or resultType != null)]", DateUtil.getTomorrow())) {
    logger.debug("write event " + e + ": " + e.title);
    writeCell(it,_case.id, 20); // case ID
    writeCell(it,division, 2); // division code
    writeCell(it,caseNumber, 30); // case number
    writeCell(it,district, 3); // district;
    writeCell(it,i++, 10); // entity number; document record identifer;
    //writeCell(it,"", 1); // action (add,change,delete);
    writeCell(it,e.type, 10); // event type (TEMP_TAB: 018)
    writeCell(it,DateUtil.format(e.startDateTime,"yyyy-MM-dd"), 10); // event date
    writeCell(it,DateUtil.format(e.startDateTime,"HH:mm"), 5); // event time
    writeCell(it,DateUtil.isAM(e.startDateTime) ? "AM" : "PM", 2); // event am/pm
    writeCell(it,e.eventLocation?.locationName?.replace("Department", ""), 10); // event room (standard dept code from la list; la to expand if needed); todo
    writeCell(it,e.resultType, 10); // event result (TEMP_TAB: 017)
    writeCell(it,e.eventLocation?.assignedPerson?.code, 10); // judge code (TEMP_TAB: 022)
    writeCell(it,e.nameExtension, 255);
    writeCell(it,"E", 1); // data source
    it.println();
  }
}

// write TEMP SCH events on the case (future events)
_tempSchFile = new File(_dir, "TEMP_SCH${_index}.dat");
logger.debug("write scheduled events info for " + _case + " to " + _tempSchFile);
_tempSchFile.withWriterAppend('ISO-8859-1') {
  i = 1;
  for (e in _case.collect("hearings[(accessLevel == null or accessLevel.ordinal() == 0) and (isStatusScheduled() or isStatusCoordinated()) and startDateTime != null && (startDateTime.after(#p1) and resultType == null)]", DateUtil.getToday())) {
    logger.debug("write event " + e + ": " + e.title);
    writeCell(it,_case.id, 20); // case ID
    writeCell(it,division, 2); // division code
    writeCell(it,caseNumber, 30); // case number
    writeCell(it,district, 3); // district;
    writeCell(it,i++, 10); // entity number; document record identifer; TODO
    //writeCell(it,"", 1); // action (add,change,delete); TODO
    writeCell(it,e.type, 10); // event type (TEMP_TAB: 018)
    writeCell(it,DateUtil.format(e.startDateTime,"yyyy-MM-dd"), 10); // event date
    writeCell(it,DateUtil.format(e.startDateTime,"HH:mm"), 5); // event time
    writeCell(it,DateUtil.isAM(e.startDateTime) ? "AM" : "PM", 2); // event am/pm
    writeCell(it,e.eventLocation?.locationName?.replace("Department", ""), 10); // event room
    writeCell(it,e.eventLocation?.parentLocation?.code, 3); // court house (standard list from LA)
    writeCell(it,e.nameExtension, 255);
    writeCell(it,"E", 1); // data source
    //writeCell(it,"", 6); // schedule room (not needed)
    it.println();
  }
}

// write TEMP PTY parties on the case
_tempPtyFile = new File(_dir, "TEMP_PTY${_index}.dat");
logger.debug("write party info for " + _case + " to " + _tempPtyFile);
litigantCond = Condition.get("Party is Litigant");
minorCond = Condition.get("Party is Minor or Incompetent");
partyIsNotRemovedCond = Condition.get("Party is Not Removed");
_tempPtyFile.withWriterAppend('ISO-8859-1') {
  i = 1;
  for (pty in _case.collect("parties[(accessLevel == null or accessLevel.ordinal() == 0) and activeNow and partyType != null and (partyType!='ATT' or !representedByParties.isEmpty())]").filter(partyIsNotRemovedCond).sortById()) {
    logger.debug("write party " + pty + ": " + pty.title);
    ptyName = pty.lfm?.replaceAll(", ", " ");
    repPtyType = pty.partyType=="ATT" ? pty.collect("representedByParties.partyType").first : "";
    akas = pty.collect("person.personAKAs.title").join(" ");
    if (akas) {
      ptyName += " " + akas;
    }
    barNumber = pty.collect("[partyType=='ATT'].identifications[identificationType=='BAR'].identificationNumber").first;
    writeCell(it,_case.id, 20); // case ID
    writeCell(it,division, 2); // division code
    writeCell(it,caseNumber, 30); // case number
    writeCell(it,district, 3); // district;
    writeCell(it,i++, 10); // entity number;
    //writeCell(it,"", 1); // action (add,change,delete);
    writeCell(it,ptyName, 255); // party name with akas
    writeCell(it,pty.partyType, 10); // party type (TEMP_TAB: 030)
    writeCell(it,repPtyType, 10); // party type that the atty is represented

    writeCell(it,"E", 1); // data source
    //writeCell(it,"", 80); // name search (not needed)
    writeCell(it,getPartyFlag(pty, minorCond, litigantCond), 1); // party flag (is litigant); todo: P - confidental
    writeCell(it,DateUtil.format(pty.dateOfBirth,"yyyy-MM-dd"), 10); // date of birth
    writeCell(it,barNumber, 20); // bar number
    writeCell(it,pty.partySubTypeLabel, 255);
    it.println();
  }
}

// generate TEMP_CXF; todo
_tempCxfFile = new File(_dir, "TEMP_CXF${_index}.dat");
logger.debug("write TEMP_CXF info to " + _tempCxfFile);
_tempCxfFile.withWriterAppend('ISO-8859-1') {
  i = 1;

  def joinedItems = DomainObject.querySQL("select ji2.case_id, ji2.dateCreated " + 
                                          "from ecourt.tcaseJoinedItem ji1 with (nolock), ecourt.tcaseJoinedItem ji2 with (nolock) " + 
                                          "where ji1.joinder_id = ji2.joinder_id " + 
                                          "and (ji1.primaryCase = 1 or ji2.primaryCase = 1) " + 
                                          "and ji1.case_id = :caseId and ji2.case_id != :caseId order by ji2.case_id", 'caseId', _case.id);

  for (joinedItem in joinedItems) {
    def relatedOn = joinedItem[1];
    if (!relatedOn) {
      continue;      
    }
    def xcase = Case.read(IdUtil.toId(joinedItem[0]));
    if (!xcase) {
      continue;      
    }

    writeCell(it,_case.id, 20); // case ID
    writeCell(it,division, 2); // division code
    writeCell(it,caseNumber, 30); // case number
    writeCell(it,district, 3); // district
    writeCell(it,i++, 10); // entity number
    writeCell(it,DateUtil.format(relatedOn, "yyyy-MM-dd"), 10); // xref date

    xdivision = null;
    if (_case.caseType == 'SC') {
      xdivision = '%S';
    } else if (_case.caseType == 'LC') {
      if (xcase.filingType == 'LEG' && xcase.legacySource == 'L') {
        xdivision = '%L';
      } else {
        xdivision = '%C';
      }
    } else {
      xdivision = 'CV';
    }

    xdept = xcase.collect("assignments[dateRemoved == null]").first;
    xdistrict = xdept?.parentLocationCode;
    xcaseNumber = xcase.caseNumber;
    if (xcase.filingType == 'LEG') {
      if (xcase.caseType != 'CU') {
        // case number ALH00SM1113 for legacy case number is 00SM1113
        xdistrict = xcaseNumber.substring(0,3);
        xcaseNumber = xcaseNumber.substring(3);
      } else {
        xdistrict = xdept?.parentOrgUnitCode;
        switch (xdistrict) {
          case 'LA_CEN': 
          xdistrict = 'LA';  // mosk / central
          break;
          case 'LA_NWE':
          xdistrict = 'NW';  // northwest district
          break;
          case 'LA_WES':
          xdistrict = 'LA';  // West District
          break;
          case 'LA_SEA':
          xdistrict = 'SE';  // Southeast District
          break;
          case 'LA_NCE':
          xdistrict = 'NCB'; // North Central District
          break;
          case 'LA_SWE':
          xdistrict = 'SW';  // Southwest District
          break;
          case 'LA_SOU':
          xdistrict = 'SO';  // South District
          break;
          case 'LA_NVA':
          xdistrict = 'NV';  // North Valley District
          break;
          case 'LA_EAS':
          xdistrict = 'EA';  // East District
          break;
          case 'LA_NEA':
          xdistrict = 'LA';  // Northeast District
          break;
          case 'LA_NOR':
          xdistrict = 'NO';  // North District
          break;
          case 'LA_SCE':
          xdistrict = 'SC';  // South Central District
          break;
          default:
            xdistrict = 'LA';  // mosk / central
          break;
        }
      }
    }

    writeCell(it,xcase.id, 20); // case ID
    writeCell(it,xdivision, 2); // xref division
    writeCell(it,xcaseNumber, 30); // xref case
    writeCell(it,"", 10); // xref type code
    it.println();

    try {
      DomainObject.clearSession();
      DomainObject.refresh(_case);
    } catch (e) {
      DomainObject.clearSession();
      DomainObject.refresh(_case);
    }
  }
  logger.debug("completed cxf output");
}

// write TEMP REG on the case
_tempRegFile = new File(_dir, "TEMP_REG${_index}.dat");
logger.debug("write register info for " + _case + " to " + _tempRegFile);
writeReg(_case, _tempRegFile);

// write TEMP DOC documents on the case
_tempDocFile = new File(_dir, "TEMP_DOC${_index}.dat");
writeDocs(_case, _tempDocFile);

def writeDocs(_case, _tempDocFile) {
  _tempDocFile.withWriterAppend('ISO-8859-1') {
    def docOffset = 0;
    def i = 1;
    while (true) {
      def documents = DomainObject.find(Document.class, "case", _case, orderBy('id'), maxResult(250), offset(docOffset));
      for (doc in documents) {
        if (doc.isAccessLevelGreaterThan(0)) {
          continue;
        } else if (['ERR','VAC', 'DEL'].contains(doc.dispositionType)) {
          continue;        
        } else if (!['F','SF','IF','I','FG','E'].contains(doc.status)) {
          continue;
        }
        logger.debug("write doc [${i}] => Document:${doc.id}:${doc.title}");
        writeCell(it,_case.id, 20); // case ID
        writeCell(it,division, 2); // division code
        writeCell(it,caseNumber, 30); // case number
        writeCell(it,district, 3); // district;
        writeCell(it,i++, 10); // entity number; document record identifer;
        //writeCell(it,"", 1); // action (add,change,delete)

        // doc type code; (TEMP_TAB: 016)
        if (doc.nameExact) {
          writeCell(it,'LEG', 10);
        } else {
          writeCell(it,doc.docNumber, 10);
        }

        writeCell(it,DateUtil.format(doc.dateFiled ?: doc.statusDate,"yyyy-MM-dd"), 10); // date filed
        writeCell(it,doc.filedByType, 10); // pty type code (TEMP_TAB: 030)

        if (doc.nameExact) {
          writeCell(it,doc.nameExact.replace('LEGACY DOCUMENT TYPE: ', ''), 255);
        } else {
          writeCell(it,doc.nameExtension, 255);
        }
        writeCell(it,doc.la_filedByText, 255);
        writeCell(it,"E", 1); // data source
        it.println();
      }

      DomainObject.clearSession();
      if (documents.size() < 250) {
        break;
      }  
      docOffset+=250;
    }
  }
  try {
    DomainObject.clearSession();
    DomainObject.refresh(_case);
  } catch (e) {
    DomainObject.clearSession();
    DomainObject.refresh(_case);
  }
}

def writeReg(_case, _tempRegFile) {
  _tempRegFile.withWriterAppend('ISO-8859-1') {
    def regOffset = 0;
    def i = 1;
    while (true) {
      def roas = DomainObject.find(ROAMessage.class, "case", _case, orderBy('timestamp'), maxResult(250), offset(regOffset));
      for (roa in roas) {
        if (!roa.timestamp || roa.message3 == 'COURT-ONLY' || roa.la_deleted) {
          continue;
        }

        logger.debug("write roa " + roa);
        writeCell(it,_case.id, 20); // case ID
        writeCell(it,division, 2); // division code
        writeCell(it,caseNumber, 30); // case number
        writeCell(it,district, 3); // district
        writeCell(it,i++, 6); // tran code
        writeCell(it,DateUtil.format(roa.timestamp,"yyyyMMdd"), 8); // tran date
        writeCell(it,DateUtil.format(roa.timestamp,"HHmmss"), 6); // tran time
        def regDate = roa.message2;
        if (!regDate) {
          regDate = DateUtil.format(roa.timestamp,"MM/dd/yyyy");
        }
        writeCell(it,regDate, 10); // reg date
        content = roa.message.replaceAll("<br/>", "; ").replaceAll("(\\r|\\n|\\r\\n)+", " ").replaceAll(" +", " ")
        .replaceAll(" ;", ";").replaceAll(":;", ":").replaceAll("\\.;", ".");
        writeCell(it,content, 1000); // reg content (to expand)
        it.println();
      }
      DomainObject.clearSession();
      if (roas.size() < 250) {
        break;
      }  
      regOffset+=250;
    }
  }
  try {
    DomainObject.clearSession();
    DomainObject.refresh(_case);
  } catch (e) {
    DomainObject.clearSession();
    DomainObject.refresh(_case);
  }
}

def getPartyFlag(pty, minorCond, litigantCond) {
  def legacyMinorStrings = ['Juvenile','Minor Plaintiff','Minor Defendant','Minor'];
  def legacyLitigantStrings = [
    'Defendant in Interpleader',
    'Defendant',
    'Court-Other County/State',
    'Defendant Erroneously Sued As',
    'Parent or Guardian',
    'Citee',
    'Debtor of Judgment Debtor',
    'Moving Party',
    'Respondent\'s DBA',
    'Plaintiff - Formerly Known As',
    'Settlement Officer-Prev Former',
    'Reviewing Court',
    'Bondee',
    'Plaintiff/Appellant In Pro Per',
    'Plaintiff/X-Defendant/Respondent',
    'AOC, Judicial Council',
    'Defendant/X-Defend/X-Compl',
    'Defendant and Cross-Complainant',
    'Court',
    'AKA',
    'Deft/X-Cmplnt/Appellant in Pro Per',
    'Independent Counsel',
    'Plaintiff/X-Defend./X-Compl.',
    'Plaintiff/Respondent',
    'Defendant/X-Complainant/Respondent',
    'Defendant/Appellant in Pro Per',
    'Defendant/Cross-Complainant/Appellant',
    'Co-Guardian',
    'Plaintiff/Petitioner\'s AKA',
    'Plaintiff\'s DBA',
    'Trustee',
    'Guardian',
    'Labor Commissioner',
    'Subject Person',
    'Petitioner\'s Requested Name',
    'Creditor',
    'Trust',
    'Respondent Appeals',
    'Plaintiff/Respondent in Pro Per',
    'Respondent\'s AKA',
    'Petitioner\'s AKA',
    'Plaintiff/Petitioner\'s DBA',
    'Intervenor',
    'Interested Party',
    'Referee',
    'Applicant',
    'Judgment Debtor',
    'Defendant/Appellant',
    'Erroneously Sued As',
    'Appellant\'s DBA',
    'Surety Company',
    'Cross-Defendant/Appellant in Pro Per',
    'Petitioner/Conservatee'
  ];

  if (minorCond.isTrue(pty) || (pty.partyType == 'LEG' && StringUtils.containsIgnoreCase(legacyMinorStrings, pty.partySubTypeLabel))) {
    return 'C';
  } else if (litigantCond.isTrue(pty) || (pty.partyType == 'LEG' && StringUtils.containsIgnoreCase(legacyLitigantStrings, pty.partySubTypeLabel))) {
    return 'L'
  }
  return 'X';    
}

def writeCell(out, value, len) {
  value = StringUtils.valueOf(value).replaceAll("\\r\\n|\\r|\\n|\\t", " ");
  if (value.length() > len) {
    value = value.substring(0, len);
  }
  out.write(StringUtils.rightPad(value, len, ' ' as char));
}












