//DOC_SET_PROPS
import com.sustain.AccessLevel;
import com.sustain.document.PrintDocumentMessage;



if (!_document.docDef.formSecurityTypes.empty) {
  accessLevel = AccessLevel.L20;
  if (_document.isInSecurityGroup('CLERICAL')) {
    accessLevel = AccessLevel.L20;
  } else if (_document.isInSecurityGroup('CLERICAL_SUPERVISOR')) {
    accessLevel = AccessLevel.L30;
  } else if (_document.isInSecurityGroup('COURTROOM')) {
    accessLevel = AccessLevel.L40;
  } else if (_document.isInSecurityGroup('MANAGER')) {
    accessLevel = AccessLevel.L45;
  } else if (_document.isInSecurityGroup('JUDGE')) {
    accessLevel = AccessLevel.L50;
  } else {
    accessLevel = AccessLevel.L20;
  }
  _document.accessLevel = AccessLevel.max(_document.accessLevel, accessLevel);
}

_case = _document.case;

//Check Doc Status
if (_document.status == null) {
  _document.status = "F";
}

// mark it as file stamped if not small claims and stamp status is null
if (_document.la_stampStatus == null && _case.caseType != 'SC') {
  _document.la_stampStatus = 'F';
}

filed = _filedDocStatus.toList().contains(_document.status);
ud_res_drugs = (_case.category == '3201' || _case.category == '3801');
if (filed && _case.caseType == 'LC' && ud_res_drugs && !_case.la_caseSecurity && ['MTN640', 'MTN650', 'STP045'].contains(_document.docNumber)) {
  logger.debug("check if the UD security doc result is ${_document.dispositionType}");
  if (_document.dispositionType == 'GRA' || _document.dispositionType == 'GIP') {
    logger.debug("motion or stip to vacate a judgement filed ${_document.entityIdAndTitle} seal case")
    _case.la_caseSecurity = 'C';
    _case.la_caseSecurityMemo = "Motion or stip to vacate a judgement filed seal case; ${_document.entityIdAndTitle}";
  }
}

// populate dates from status date
if (filed && _document.statusDate && !_document.statusDate.equals(_document.dateFiled)) {
  _document.dateFiled = _document.statusDate;
  logger.info("document dateFiled set to " + _document.statusDate);
} else if (_document.status == _recvDocStatus && _document.statusDate && !_document.statusDate.equals(_document.dateReceived)) {
  _document.dateFiled = null;
  _document.dateReceived = _document.statusDate;
  logger.info("document dateReceived set to " + _document.statusDate);
} else if (!filed) {
  _document.dateFiled = null;
}

if (!_document.dateReceived) {
  _document.dateReceived = _document.statusDate ? _document.statusDate : new Date();
}
_document.saveOrUpdate();

// update party to served
if (filed && _document.docDef.formGroups.contains(_serviceDocType) || (_document.docNumber == "POS160" && _document.kv("Proof of Mailing Date") != null)) {
  logger.info("check for party to set as served");
  if (_document.docNumber == 'POS160') {
    kwValue = _document.getKeywordValue("Proof of Mailing Date");
  } else {
    if (['POS100', 'POS125', 'POS130'].contains(_document.docNumber)) {
      kwValue = _document.getKeywordValue("Mailing Date");
    } else {
      kwValue = _document.getKeywordValue("Service Date");
    }
  }
  serviceDate = DateUtil.parseSafe(kwValue);
  if (!serviceDate) {
    serviceDate = _document.statusDate;
  }
  if (DateUtil.nextDay(new Date()).before(serviceDate)) {
    addError("Service date " + serviceDate + " cannot be a future date")
  }

  servedPath = "refersTo[#p1.contains(partyType) and (status == null or #p2.contains(status))]";
  served = _document.collect(servedPath, _statusPartyTypes.toList(), _preServedStatuses.toList());
  if (_revertPartyStatusDocDispo.contains(_document.dispositionType)) {
    //Document is invalid...revert party statuses
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": served, "status": _servedStatus, "statusDate": serviceDate, "doc": _document, "removeStatus": true]).getOutputValue("returnParties");
  } else {
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": served, "status": _servedStatus, "statusDate": serviceDate, "doc": _document]).getOutputValue("returnParties");
    for (party in served) {
      party.la_serviceDoc = _document.docNumber;
      party.la_serviceDocFilingDate = _document.filedOrStatusDate;
      party.saveOrUpdate();
      logger.debug("party2:" + party);
      logger.info("set " + party.fullName + " as served: " + _servedStatus);
    }
  }
}

// update party to answered
if (filed && _document.docDef.formGroups.contains(_answerDocType)) {
  logger.info("check for party to set as answered");
  //servedPath = "filedBy[#p1.contains(partyType) and (status == null or #p2.contains(status))]";
  //served = _document.collect(servedPath , _statusPartyTypes.toList(), _preAnsStatuses.toList());
  servedPath = "filedBy[#p1.contains(partyType)]";
  served = _document.collect(servedPath, _statusPartyTypes.toList());
  if (_revertPartyStatusDocDispo.contains(_document.dispositionType)) {
    //Document is invalid...revert party statuses
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": served, "status": _answeredStatus, "statusDate": _document.statusDate, "doc": _document, "removeStatus": true]).getOutputValue("returnParties");
  } else {
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": served, "status": _answeredStatus, "statusDate": _document.statusDate, "doc": _document]).getOutputValue("returnParties");
  }
}

// update party to responded
if (filed && _document.docDef.formGroups.contains(_responseDocType)) {
  logger.info("check for party to set as responded");
  //servedPath = "filedBy[#p1.contains(partyType) and (status == null or #p2.contains(status))]";
  //served = _document.collect(servedPath , _statusPartyTypes.toList(), _preResStatuses.toList());
  servedPath = "filedBy[#p1.contains(partyType)]";
  served = _document.collect(servedPath, _statusPartyTypes.toList());
  if (_revertPartyStatusDocDispo.contains(_document.dispositionType)) {
    //Document is invalid...revert party statuses
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": served, "status": _responseStatus, "statusDate": _document.statusDate, "doc": _document, "removeStatus": true]).getOutputValue("returnParties");
  } else {
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": served, "status": _responseStatus, "statusDate": _document.statusDate, "doc": _document]).getOutputValue("returnParties");
  }
}

if (_document.accessContext.insertScreen) {
  runRuleInCurrentContext("DOCUMENT_COPY_ADDRESS_TOFILED_BY", ["document": _document]);
}

if (_document.status == 'RP') {
  //futureHearings = _document.collect("crossReferencedScheduledEvents[activeToday]");
  futureHearings = _document.collect("relatedScheduledEvents[activeToday]");
  if (!futureHearings.empty) {
    addWarning("Document is related to future hearing, are you sure the status Proposed - Received is correct? Proposed Received are sent directly to the judge.");
  }
} else if (_document.status == 'RPHRG') {
  //futureHearings = _document.collect("crossReferencedScheduledEvents[activeToday]");
  futureHearings = _document.collect("relatedScheduledEvents[activeToday]");
  if (futureHearings.empty) {
    // allow no future hearing relation if related to ex parte application
    if (_document.collect("relatedDocuments[docNumber=='APP060']").empty) {
      addError("Proposed - Received (Hearing Set) must be related to future hearing date.");
    }
  }
}

if (filed && _document.docNumber == "PJ020" && _document.kv('Type') == 'Full Satisfaction') {
  // ack of judgment
  found = false;
  filedBy = _document.filedBy;
  asTo = _document.refersTo;
  filedByIds = DomainObject.ids(filedBy);
  asToIds = DomainObject.ids(asTo);
  for (award in _document.collect("subCase.judgments.awards")) {
    if (award.collect("parties[judgmentPartyType=='JUD_FOR'].party.id").containsAll(filedByIds) &&
        award.collect("parties[judgmentPartyType=='JUD_AGAINST'].party.id").containsAll(asToIds)) {
      award.status = "SAT";
      award.statusDate = new Date();
      found = true;
      addMessage("Judgment on " + DateUtil.format(award.awardDate) + " status set to Satisfied");
      break;
    }
  }
  if (!found) {
    addWarning("No matching judgment found for " + filedBy.collect("title").join() + " and against " + asTo.collect("title").join() + ", are you sure?");
  }
}

if (filed && _document.docNumber == "PJ020" && _document.dispositionType == 'DEL') {
  found = false;
  filedBy = _document.filedBy;
  asTo = _document.refersTo;
  filedByIds = DomainObject.ids(filedBy);
  asToIds = DomainObject.ids(asTo);
  for (award in _document.collect("subCase.judgments.awards")) {
    if (award.collect("parties[judgmentPartyType=='JUD_FOR'].party.id").containsAll(filedByIds) &&
        award.collect("parties[judgmentPartyType=='JUD_AGAINST'].party.id").containsAll(asToIds)) {
      if (award.previousStatus){
        logger.debug("Previous status ${award.previousStatus}, current ${award.status}");
        award.status = award.previousStatus.value;
        award.statusDate = new Date();
        addMessage("Judgment on ${DateUtil.format(award.awardDate)} status set to previous status");
      }else{
        addMessage("The status of the Judgment on ${DateUtil.format(award.awardDate)} will remain as '${award.statusLabel}'");
      }
      found = true;
      break;
    }
  }
}


if (filed && (_document.docNumber == "DEC060" && _document.kv("Satisfied") == "Yes") || _document.docNumber == "PJ160") {
  for (award in _document.judgmentAwards) {
    award.status = "SAT";
    award.statusDate = new Date();
    addMessage("Judgment on " + DateUtil.format(award.awardDate) + " status set to Satified");
  }
}

// notice of settlement, lets vacate events and set OSC re: Dismissal
if (filed && _document.docNumber == "NTC340") {
  runRuleInCurrentContext("DOCUMENT_NOS_OSC_REDISMISSAL", ["document": _document]);
}

// auto generated documents
autoGenerated = ["FW003": "FW001"];
for (autoGen in autoGenerated) {
  number1 = autoGen.key;
  number2 = autoGen.value;

  // if document number matches: auto generate doc number, status is signed & filed, and doc is waiting for scan
  if (_document.docNumber == number1 && _document.status == "SF" && _document.waitingForScanStorageStatus && _document.la_configMemo == "AUTO_GEN" && _document.efmReferenceId == null) {
    //addError("generate FW003");
    logger.debug("document in auto generate " + number1 + " see if related " + number2 + " is available");
    logger.debug("document3:" + _document);
    logger.debug("documentNumber:" + _document.docNumber);
    pty = _document.refersTo.first;
    logger.debug("_document.refersTo:" + _document.refersTo);
    logger.debug("_document.filedBy=${_document.filedBy}");

    relatedDoc = _document.collect("subCase.documents[docNumber==#p1 and dispositionType == null and filedBy.contains(#p2)]", number2, pty).first;
    logger.debug("relatedDoc:" + relatedDoc);
    logger.debug("pty:" + pty);
    if (!relatedDoc) {
      addError("Related " + DocDef.get(number2).name + " not found for " + DocDef.get(number1).name + " by " + pty.fullName);
    } else {
      generatePars = [(number2.toLowerCase()): relatedDoc, "party": pty, (number1.toLowerCase()): _document];
      logger.debug("document auto generate " + number1 + " with " + generatePars);
      runRuleInCurrentContext("DOC_GENERATE_" + number1, generatePars);
      logger.debug("FW003.SET : ${_document.case.currentAssignment}");
      _document.la_configMemo == null;
    }
  }
}

// Assignment of Judgment
if (_document.docNumber == "PJ090") {
  runRule("DOC_SET_PROPS_PJ090", ["document": _document]);  
}

//Opposition filed for Undersubmission -- adjust the timestandard
if (_document.docNumber == "SUP030") {
  _document.relatedDocuments.each() { relatedDoc ->
    relatedDoc.relatedScheduledEvents.each() { event ->
      event.submissions.each() { submission ->
        if (submission.memo == 'Waitng Opposition' && submission.dateStarted.after(_document.dateFiled)) {
          submission.dateStarted = _document.dateFiled;
          submission.saveOrUpdate();
        }
      }
    }
  }
}

//Reply filed for Undersubmission -- adjust the timestandard
if (_document.docNumber == "SUP040") {
  _document.relatedDocuments.each() { relatedDoc ->
    relatedDoc.relatedScheduledEvents.each() { event ->
      event.submissions.each() { submission ->
        if (submission.memo == 'Waitng Reply' && submission.dateStarted.after(_document.dateFiled)) {
          submission.dateStarted = _document.dateFiled;
          submission.saveOrUpdate();
        }
      }
    }
  }
}

// Notice of Bankruptcy set stay on case or party
if (_document.docNumber == "NTC360") {
  if (_document.kv("Bankruptcy Stay Case Type") == "Case") {
    css = _document.collect("case.specialStatuses[status=='BK' and endDate == null]").first;
    if (!css) {
      css = new CaseSpecialStatus();
      css.status = "BK";
      _document.case.add(css, "specialStatuses");
    }
    css.startDate = _document.dateFiled;
    css.saveOrUpdate();
  } else {
    def stayParties = [];
    if (_document.refersTo.size() > 0) {
      stayParties = _document.refersTo;
    } else {
      stayParties = _document.filedBy;
    }
    for (pty in stayParties) {
      pty.stayStatus = "BK";
      pty.stayDate = _document.dateFiled;
      pty.saveOrUpdate();
    }
  }
  hearing = new ScheduledEvent();
  hearing.type = "NON020";
  hearing.nameExtension = "Re: Bankruptcy"
  hearing.startDateTime = DateUtil.combine(DateUtil.add(_document.statusDate, "180b_"), DateUtil.parse("8:30 AM"));
  hearing.endDateTime = hearing.startDateTime;
  hearing.eventLocation = _document.case.collect("assignments[dateRemoved == null]").last().dirLocation;
  _document.case.add(hearing, "hearings");
  hearing.saveOrUpdateAndExecuteEntityRules(true, false);
}

// filed by government agency update filing parties
if (_document.la_filedByGovt) {
  logger.debug("${_document.entityIdAndTitle} marked filed by govt, set all filed by parties to gc6103")
  for (pty in _document.filedBy) {
    logger.debug("${pty.entityIdAndTitle} marked as govt gc6103")
    pty.person.governmentAgency = true;
    pty.person.saveOrUpdate();
  }
}

if (_document.docNumber == 'WRIT055') {
  for (writ in _document.collect("relatedDocuments[docNumber=='WRIT030' and dispositionType==null]")) {
    writ.dispositionType = 'RF';
    writ.dispositionDate = _document.dateFiled;
    writ.saveOrUpdate();
  }
}

if (_document.docNumber == 'PJ040' && _document.dispositionType == 'GRA') {
  for (writ in _document.collect("relatedDocuments[docNumber=='WRIT030' and dispositionType==null]")) {
    writ.dispositionType = 'LOST';
    writ.dispositionDate = _document.dateFiled;
    writ.saveOrUpdate();
  }
}

if (_document.docNumber == 'WRIT030') {
  def county = _document.kv("FillText74");
  if (!county) {
    county = _document.kv("County");
  }
  if (county) {
    _document.nameExtension = "(" + county + ")"
  }
}
//Set Refers_to when generating fee waiver orders reading from filed doc
/*if((_document.docDefNumber == 'FW003' || _document.docDefNumber == 'FW007' || _document.docDefNumber == 'FW008' || _document.docDefNumber == 'FW011' || _document.docDefNumber == 'FW012') && !_document.refersTo)  {
logger.debug("_document"+_document.crossReferencedDocuments)
_document.crossReferencedDocuments.each() { xrefDoc->
if((xrefDoc.docDefNumber == 'FW001' || xrefDoc.docDefNumber == 'FW002' ||  xrefDoc.docDefNumber == 'FW006' || xrefDoc.docDefNumber == 'FW010') && xrefDoc.refersTo) {
_document.addCrossReferences("REFERS_TO",xrefDoc.refersTo)
}
}
logger.debug("_document.refersTo:"+_document.refersTo);
}*/

/**
if (_document.dispositionType && (_document.docNumber == "FW002" || _document.docNumber == "FW001")) {granted = (_document.dispositionType == "GRA" || _document.dispositionType == "GIP");
logger.debug("${_document.entityIdAndTitle} is fw001 with result each filed by party as fw001 as ${granted}");
for (pty in _document.filedBy) {if (_document.docNumber == "FW001") {logger.debug("${pty.entityIdAndTitle} la_fw001 set to ${granted}");
pty.la_fw001 = granted;} else {logger.debug("${pty.entityIdAndTitle} la_fw002 set to ${granted}");
pty.la_fw002 = granted;}pty.saveOrUpdate();}}*/

if (_document.docDefNumber == 'RUL010') {
  _document.filedByType = "CLK";
}

//Proof of Service - Order Granting Attorney's Motion to be Relieved as Counsel
//Order on Application to be Relieved as Attorney on Completion of Limited Scope Representation
if ((_document.docDefNumber == 'POS146' || _document.docDefNumber == 'ORD130') && (_document.status == 'F' || _document.status == 'IF' || _document.status == 'SF' || _document.status == 'FG')) {
  def attys = [];
  def parties = [];
  //Try the find the attorneys and parties from the current document
  // attys = _document.collect("crossReferencedParties[partyType=='ATT' and endDate == null]");
  // parties = _document.collect("crossReferencedParties[partyType!='ATT']");
  attys = _document.collect("relatedParties[partyType=='ATT' and endDate == null]");
  parties = _document.collect("relatedParties[partyType!='ATT']");
  //Try the find the attorneys and parties from the related document
  if (attys.size() == 0) {
    //attys = _document.collect("relatedDocuments.crossReferencedParties[partyType=='ATT' and endDate == null]")
    attys = _document.collect("relatedDocuments.relatedParties[partyType=='ATT' and endDate == null]")
  }
  if (parties.size() == 0) {
    //parties = _document.collect("relatedDocuments.crossReferencedParties[partyType!='ATT']")
    parties = _document.collect("relatedDocuments.relatedParties[partyType!='ATT']")
  }
  for (atty in attys) {
    atty.endDate = _document.dateFiled;
    //If _document.docDefNumber == 'ORD130' and parties not found then try to get the parties from the representation
    if ((_document.docDefNumber == 'ORD130') && parties.size() == 0) {
      parties = atty.representedByParties;
    }
    for (party in parties) {
      if (CollectionUtil.contains(atty.representedByParties, party)) {
        //Make party self represented only if the attorney is the only attorney representing the party
        //and the party is not represented by any other attorney
        if (party.representedByParties.size() == 1) {
          party.selfRepresented = 'Y';
          if (party.address == null) {
            addWarning("Party ${party.fml} is now self represented and has no address.  Please enter the party address")
          }
          party.saveOrUpdate();
        }
        atty.saveOrUpdate();
        for (xref in party.getChildPartyRepresentationsByType(atty,"REPRESENTEDBY")) {
          xref.representationType = "REPLACE";
          xref.saveOrUpdate();
        }
        party.representationText = party.getRepresentationLabel("REPRESENTEDBY");
        party.saveOrUpdate();

        atty.representationText = atty.getRepresentationLabel("REPRESENTEDBY");
        atty.saveOrUpdate();
      }
    }
  }
}

//Pro Hac Vice
if (_document.docDefNumber == 'MTN390' || _document.docDefNumber == 'MTN003') {
  //Find out attorneys and parties
  //attys = _document.collect("crossReferencedParties[partyType=='ATT']");
  //parties = _document.collect("crossReferencedParties[partyType!='ATT']");
  attys = _document.collect("relatedParties[partyType=='ATT' and endDate == null]");
  parties = _document.collect("relatedParties[partyType!='ATT']");
  newAttys = _document.collect("documentParties[type=='ATTY_N'].party[partyType == 'ATT']");
  logger.debug("attys:" + attys);
  logger.debug("parties:" + parties);
  //Pro Hac Vice Motion Added -- Set Representation, if not set already
  for (Party party : parties) {
    if (!party.getRepresentation().containsAny(attys)) {
      party.selfRepresented = null;
      //party.addCrossReferences("REPRESENTEDBY",attys);
      party.addPartyRepresentations(party, attys, "REPRESENTEDBY");
      party.saveOrUpdate();
    }
  }
  if(newAttys) {
    _document.addDocumentParties(_document,newAttys,"FILEDBY");
  }
  //Pro Hac Vice Motion Granted
  def dispo = _document.dispositionType;
  logger.debug("_document.dispositionType:" + _document.dispositionType);
  if (dispo == "GRA" || dispo == "DEN") {
    //Set attorny sub type as Pro Hac Vice
    for (atty in attys) {
      if (dispo == "GRA") {
        if (atty.partySubType != "PHV") {
          atty.partySubType = "PHV";
        }
        if (atty.proposedName != "A") {
          atty.proposedName = "A";
          atty.la_proHacViceStatusDate = _document.dispositionDate;
        }
      }
      if (dispo == "DEN") {
        if (atty.proposedName != "D") {
          atty.proposedName = "D";
          atty.la_proHacViceStatusDate = _document.dispositionDate;
        }
      }
      atty.saveOrUpdateAndExecuteEntityRules(true, false);
    }
  }
}

//Stay created due to filing of POS148
if (_document.docDefNumber == 'POS148') {
  //get active specialStatus
  activeSpecialStatuses = _document.collect("case.specialStatuses[status=='STAY' and activeNow==true]");
  if (activeSpecialStatuses.size() > 0) {
    //Update the special Status
    serviceDate = DateUtil.parse(_document.getKeywordValue("Service Date"));
    for (specialStatus in activeSpecialStatuses) {
      if (specialStatus.startDate != serviceDate) {
        specialStatus.startDate = serviceDate;
        specialStatus.endDate = DateUtil.addDays(serviceDate, 30);
        specialStatus.saveOrUpdateAndExecuteEntityRules(true, false);
      }
    }
  } else {
    logger.debug("Service Date = " + _document.getKeywordValue("Service Date"));
    specialStatus = new CaseSpecialStatus();
    serviceDate = DateUtil.parse(_document.getKeywordValue("Service Date"));
    endDate = DateUtil.addDays(serviceDate, 30)
    specialStatus.startDate = serviceDate;
    specialStatus.status = 'STAY';
    specialStatus.endDate = endDate;
    specialStatus.case = _document.case;
    specialStatus.memo = "STAY created due to filing of POS148 -  Proof of Service (Sister State Judgment)";
    _document.case.specialStatuses.add(specialStatus);
    specialStatus.saveOrUpdateAndExecuteEntityRules(true, false);
  }
}

//Add marginal notation if the document is stricken
if (_document.dispositionType == "STR" && _document.stored) {
  logger.debug("apply text stricken marginal notation")
  _document.textStamp("Ordered Stricken on " + DateUtil.formatDateTime(_document.dispositionDate), 90, 20, 200, 12, null, null, null);
} else if (_document.dispositionType == 'VOD' && _document.stored) {
  logger.debug("apply text void marginal notation")
  _document.textStamp("Ordered Voided on " + DateUtil.formatDateTime(_document.dispositionDate), 90, 20, 200, 12, null, null, null);
}

//Make party named defendant when PJ120 Claim of Right to Possession and Notice of Hearing is granted
if (_document.docNumber == "PJ120" && _document.dispositionType == "GRA") {
  def parties = _document.collect("filedBy[partyType == 'ARRCLM' and status == null]")
  for (party in parties) {
    party.partyType = "DEF";
    party.status = "NAMED";
    party.saveOrUpdate();
    runRule("SUBCASE_DISPOSITION", ["subCase": party.subCase]);
  }
}

// Declaration re: Uninsured Motorist set stay on case or party
if (_document.docNumber == "DEC085") {
  if (_document.kv("Uninsured Motorist Stay Case") == "Yes") {
    css = _document.collect("case.specialStatuses[status=='UIM' and endDate == null]").first;
    if (!css) {
      css = new CaseSpecialStatus();
      css.status = "UIM";
      _document.case.add(css, "specialStatuses");
    }
    css.startDate = _document.dateFiled;
    css.endDate = DateUtil.addDays(_document.dateFiled, 180);
    css.saveOrUpdate();
  } else {
    for (pty in _document.filedBy) {
      pty.stayStatus = "UIM";
      pty.stayDate = _document.dateFiled;
      pty.saveOrUpdate();
    }
  }
}

//For DOC POS150 check if After Substituted Service of Summons & Complaint  is Yes and if Yes update AS TO  Party to Served.
if (_document.docNumber.equalsIgnoreCase("POS150")) {
  service = (_document.collect("documentKeywords[keywordName=='After Substituted Service of Summons & Complaint ?']").first == null) ? null : _document.collect("documentKeywords[keywordName=='After Substituted Service of Summons & Complaint ?']").first.keywordValue;
  logger.debug("Service : ${service}")
  if (service != null && service.equalsIgnoreCase("Yes")) {
    //Get as to parties
    asToParties = _document.refersTo;
    logger.debug("AS To : ${asToParties}");
    def updatedParties = runRule("SET_CIVIL_PARTY_STATUS", ["parties": asToParties, "status": _servedStatus, "statusDate": _document.dateFiled, "doc": _document]).getOutputValue("returnParties");
  }
}
//If ADM100 is added, then Generate ADM100 and print
/*if(_document.docNumber.equalsIgnoreCase("ADM100")) {
document = _document.generate("ADM100", _document.case, [:]);
document.nameExtension = "(Default)";
document.saveOrUpdate();
addPrintMessage(_document);
}*/

//If NTC177 is filed, Check for Bankruptcy Stay and remove it and schedule a  Status Conference on the first available date on or after 45 days
if (_document.docNumber == 'NTC177') {
  non020Hearings = _document.case.collect("futureEvents[type=='NON020']");
  vacateNon020Text = non020Hearings.size() > 0 ? "vacate the outstanding Non-Appearance Case Review hearing(s), " : "";

  addWarning("Filing this document will ${vacateNon020Text}remove the bankruptcy stay and schedule a Status Conference on the first available date on or after 45 days.");
  activeSpecialStatuses = _document.collect("case.specialStatuses[status=='BK' and isActiveNow()]");
  for (specialStatus in activeSpecialStatuses) {
    specialStatus.endDate = _document.dateFiled; ;
      specialStatus.saveOrUpdateAndExecuteEntityRules(true, false);
  }

  //Schedule the CON057 hearing
  ScheduledEvent sc = new ScheduledEvent();
  sc.type = "CON057";
  startDateTime = DateUtil.combine(DateUtil.getBusinessDayNext(DateUtil.addDays(_document.dateFiled, 45)), DateUtil.parse("8:30 AM"));
  sc.startDateTime = startDateTime;
  sc.endDateTime = startDateTime;
  sc.eventLocation = _document.case.dirLocation;
  _document.case.add(sc, "hearings");
  sc.saveOrUpdateAndExecuteEntityRules(true, false);

  //Vacate outstanding NTC020 - Non-Appearance Case Review hearing(s)
  for (non020Hearing in non020Hearings) {
    non020Hearing.resultType = "VAC";
    non020Hearing.eventStatus = "CANCELED";
    non020Hearing.resultDate = new Date();
  }
}

//If NTC175 is filed,  schedule a  OSC re: Dismissal  on the first available date on or after 45 days
if (_document.docNumber == 'NTC175') {
  addWarning("Filing this document will schedule a OSC - Failure to File Dismissal on the first available date on or after 45 days");
  //Now schedule the hearing
  ScheduledEvent sc = new ScheduledEvent();
  sc.type = "HRG110";

  startDateTime = DateUtil.combine(DateUtil.getBusinessDayNext(DateUtil.addDays(_document.dateFiled, 45)), DateUtil.parse("8:30 AM"));

  sc.startDateTime = startDateTime;
  sc.endDateTime = startDateTime;
  sc.eventLocation = _document.case.dirLocation;
  _document.case.add(sc, "hearings");
  sc.saveOrUpdateAndExecuteEntityRules(true, false);

}

//If REQ070 if filed and related to Dismissal, set the la_defaultDocument on the dismissal
if (_document.docNumber == "REQ070") {
  for (dismissal in _document.findByXRef("Dismissal", "REFERS_TO")) {
    dismissal.la_defaultDocument = _document;
    _document.dismissals.add(dismissal);
    dismissal.saveOrUpdate();
  }
}

//Revert Amendment of SubCase if Complaint on an Amended Case is Stricken
/*
def lastAmendedSubCase = _document.subCase?.collect("amendings")?.last();
if (_document.docNumber == "COM040" && _revertPartyStatusDocDispo.contains(_document.dispositionType) && lastAmendedSubCase != null) {
  addWarning("This action will restore the previous version: ${lastAmendedSubCase.subCaseName} subcase: ${_document.subCase}");  
  runRule("REVERT_LAST_SUBCASE_AMENDMENT", ["subCase": _document.subCase]);
}
*/
def lastAmendedSubCase = _document.collect("subCase.amendings.amended").first();
if ((_document.docNumber == "COM040" || _document.docNumber == "COM045" || _document.docNumber == "COM075") && _revertPartyStatusDocDispo.contains(_document.dispositionType) && lastAmendedSubCase != null) {
  addWarning("This action will restore the previous version: " + lastAmendedSubCase.subCaseName);
  runRule("REVERT_LAST_SUBCASE_AMENDMENT", ["subCase": _document.subCase]);
}

//If TRL060 is added, then Generate TRL060 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("TRL060") && !_document.stored) {
  def exhibitList = _document.findByXRef("ExhibitList", "REFERS_TO").first();
  if (exhibitList) {
    _document.generateTemplate(["Exhibits": exhibitList.exhibits]);
    addPrintMessage(_document);
    _document.la_configMemo = null;
  }
}

//If NTC070 is added, then Generate NTC070 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC070") && !_document.stored) {
  def caseAssignment = _document.case.collect("assignments[dateRemoved == null]").first
  def previousAssignment = _document.case.collect("assignments[dateRemoved != null]").last;
  _document.generateTemplate(["CaseAssignment": caseAssignment, "previousCaseAssignment": previousAssignment, "CasePerson": _document.case.la_mailingList, "mailingDate": new Date()]);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}

//If NTC080 is added, then Generate NTC080 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC080") && !_document.stored) {
  def caseAssignment = _document.case.collect("assignments[dateRemoved == null]").first
  def previousAssignment = _document.case.collect("assignments[dateRemoved != null]").last;
  _document.generateTemplate(["CaseAssignment": caseAssignment, "previousCaseAssignment": previousAssignment, "CasePerson": _document.case.la_mailingList, "mailingDate": new Date()]);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}

//If NTC430 - Notice of Removal to Federal Court is filed insert the RFC case special status
if (_document.docNumber.equalsIgnoreCase("NTC430")) {
  def rfcStatuses = _document.case.collect("specialStatuses[status == 'RFC' and endDate == null]")
  if (rfcStatuses.size() == 0) {
    CaseSpecialStatus status = new CaseSpecialStatus();
    status.status = "RFC";
    status.startDate = _document.filedOrStatusDate;
    status.case = _document.case;
    _document.case.specialStatuses.add(status);
    status.saveOrUpdateAndExecuteEntityRules(true, false);
  }
}

//If NTC313 - Notice of Remand from Federal Court is filed, update the end date on the RFC case special status
if (_document.docNumber.equalsIgnoreCase("NTC313")) {
  def rfcStatuses = _document.case.collect("specialStatuses[status == 'RFC' and endDate == null]");
  def dateRemanded = DateUtil.parseSafe(_document.getKeywordValue("Date Remanded back to Superior Court"));
  for (status in rfcStatuses) {
    status.endDate = dateRemanded;
    status.saveOrUpdate();
  }
}

//If NTC309 is added, then Generate NTC309 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC309") && !_document.stored) {
  _document.generateTemplate(["mailingDate": DateUtil.getToday()]);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}

//Copy the refers to from the motion to the order
if (_document.docDefNumber == 'ORD130') {
  def application = _document.collect("relatedDocuments[docNumber=='MTN005']").first;
  if (application?.refersTo?.size() > 0 && _document.refersTo.size() == 0) {
    //_document.addCrossReferences("REFERS_TO",application.refersTo)
    _document.addDocumentParties(_document, application.refersTo, "REFERS_TO");
  }
}

//If a Judgment document is entered and related to a judgment award which is related to a minute order
//Relate the judgement document to the minute order
if (_document.docDef.formType == 'JMT') {
  for (award in _document.judgmentAwards) {
    //If the document is granted and filed then update the judgment award to Entered
    if (_document.dispositionType == 'GRA' && (_document.status == 'F' || _document.status == 'I' || _document.status == 'IF' || _document.status == 'SF' || _document.status == 'E' || _document.status == 'FG') && award.status == 'PR') {
      award.statusDate = _document.dispositionDate;
      award.status = 'ENT';
      award.saveOrUpdateAndExecuteEntityRules(true, false);
    }
    for (otherAwardDoc in award.documents) {
      if (otherAwardDoc.id != _document.id) {
        // _document.addCrossReference(otherAwardDoc,"DOCREL");
        _document.addChildRelatedDocument(_document, otherAwardDoc, "DOCREL");
      }
    }
  }
}

//If NTC050 is added, then Generate NTC050 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC050") && !_document.stored) {
  def params = [:];
  params.put("Case", _document.case);
  params.put("mailingDate", DateUtil.getToday());
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  logger.debug(event);
  if (event) {
    params.put("Event", event);
  }  
  def joCode = _document.case.collect("currentAssignment.joCode").first();
  if (joCode == null) {
    joCode = event?.rescheduledTo?.la_rescheduleToJOCode;
  }
  if (joCode != null && DirPerson.getByCode(joCode)?.isAttachmentAvailable('SIG')) {
    params.put("CommonManual_joSignature", joCode);
    _document.generateTemplate(params);
    addPrintMessage(_document);
    _document.la_configMemo = null;
  } else {
    addError("Must assign Judicial Officer with signature on file to case ${_document.case.caseNumber} to generate ${_document.docDef.name} automatically.");
    logger.debug('throw error');
  }
}

//IR609833 AB3088 Aman modified on 10/07/2020 for auto-generated PDF issue from GENERATE DOCUMENT PANEL in eCourt. If NTCCOV001 is added, then Generate NTCCOV001 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTCCOV001") && !_document.stored) {
  def params = [:];
  params.put("Case", _document.case);
  params.put("mailingDate", DateUtil.getToday());
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  logger.debug(event);
  if (event) {
    params.put("Event", event);
  }  
  def mailingList = _document.case.collect("la_mailingList[(partyType != 'DEF') or (partyType == 'DEF' and (status == 'RES' or status == 'ANS'))]");
  params.put("CasePerson", mailingList);
  _document.generateTemplate(params);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}

//Auto generate OSC notices
if (_document.la_configMemo == "AUTO_GEN" && ['OSC020', 'OSC030', 'OSC045', 'OSC060'].contains(_document.docNumber) && !_document.stored) {
  //Check for JO Signature
  def jo = _document.case.collect("assignments[dateRemoved == null]").first();
  if (jo?.dirPerson?.isImageAttachmentAvailable('SIG')) {
    def params = [:];
    //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
    def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
    if (event) {
      params.put("Event", event);
    }
    if (_document.docNumber.equalsIgnoreCase('OSC045')) {
      params.put("CaseAssignment", jo);
    }
    params.put("Case", _document.case);
    params.put("mailingDate", DateUtil.getToday())
    params.put("CommonManual_joSignature", jo.joCode)
    _document.generateTemplate(params);
    addPrintMessage(_document);
    _document.la_configMemo = null;
  } else {
    addWarning("No Signature file found for Judicial Officer. ${_document.docDef.name} will not get generated automatically.  Please generate this notice manually.");
  }
}

//If NTC245 is added flagged for autogeneration, then Generate NTC245 and print
if (_document.docNumber.equalsIgnoreCase("NTC245") && !_document.stored && _document.la_configMemo == "AUTO_GEN") {
  def joCode = _document.case.collect("currentAssignment.joCode").first;
  //if case assignment is not found try to get the jo code from the rescheduled to event
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  if (joCode == null) {
    joCode = event?.rescheduledTo?.la_rescheduleToJOCode;
  }
  if (joCode != null && DirPerson.getByCode(joCode)?.isAttachmentAvailable('SIG')) {
    def params = [:];
    params.put("Case", _document.case);
    params.put("mailingDate", DateUtil.getToday())
    params.put("Event", event);
    params.put("CommonManual_joSignature", joCode);
    _document.generateTemplate(params);
    addPrintMessage(_document);
    _document.la_configMemo = null;
  } else {
    addError("Must assign Judicial Officer with signature on file to case ${_document.case.caseNumber} to generate ${_document.docDef.name} automatically.");
    logger.debug('throw error');
  }
}

//If NTC247 is added, generate template
if (_document.docNumber.equalsIgnoreCase("NTC247") && !_document.stored && _document.la_configMemo == "AUTO_GEN") {
  //def event = _document.findByXRef("ScheduledEvent", "DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  def jo = _document.case.collect("assignments[dateRemoved == null]").first();
  def joCode = jo?.joCode;

  if (jo != null && joCode != null && jo.dirPerson.isAttachmentAvailable('SIG')) {

    def params = [:];
    params.put("Case", _document.case);
    params.put("Event", event);
    params.put("CaseAssignment", jo);
    params.put("CommonManual_joSignature", joCode);

    pj080Doc = _document.case.collect("documents[docNumber=='PJ080']").orderBy("filedOrStatusDate desc").first()

    if (!pj080Doc) {
      addError("Cannot generate '${_document.docDef.name}' automatically. No 'PJ080- Application for Order of Sale of Dwelling' found on this case.");
      //Note: This scenario is virtually impossible. if needed, automatically delete the document when this occurs.
    } else {
      asToParties = pj080Doc.refersTo.orderBy("partyNumber");
      asToPartiesText = RichList.toRichList(asToParties).joinOxford('fml');

      regardingText = "";
      regardingText2 = "";

      if (asToParties.size() == 1) {
        regardingText = asToPartiesText;
      } else if (asToParties.size() >= 2) {
        if (asToPartiesText.length() <= 39) {
          regardingText = asToPartiesText;
        } else {
          regardingText = "multiple parties *";
          regardingText2 = "*" + asToPartiesText;
        }
      }

      Document ntc247 = _document;
      ntc247.la_configMemo = null;
      ntc247.saveOrUpdate();

      //event.addCrossReference(ntc247, "DOCREL");
      event.addDocumentEvent(ntc247, event, "DOCREL");

      params.put("regarding", regardingText);
      params.put("regarding2", regardingText2);
      params.put("Event", event);
      logger.debug("Generating [${ntc247.title}] ")
      ntc247.generateTemplate(params);
      addPrintMessage(ntc247);
      _document.la_configMemo = null;
    }

  } else {
    addError("Must assign Judicial Officer with signature on file to case ${_document.case.caseNumber} to generate ${_document.docDef.name} automatically.");
    logger.debug('throw error');
  }
}

//Dispose the case if the case initiating petition is disposed
if (_document.case.caseType != 'JC' && (_document.docDef.formType == 'PET' || _document.docDef.formType == 'NC') && (_document.la_caseInitDoc || _document.case.category=="4305") && _document.dispositionType != null && _document.case.dispositionType == null) {  
  if (_petitionGrantedDisp.toList().contains(_document.dispositionType)) {
    setCaseDisposition(_document.case, '3400G', _document.dispositionDate);
  } else if (_petitionDeniedDisp.toList().contains(_document.dispositionType)) {
    setCaseDisposition(_document.case, '3400D', _document.dispositionDate);
  }
}
//If MISC110 is added, then Generate MISC110 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("MISC110") && !_document.stored) {
  def docs = [];
  for (transfer in _document.findByXRef("CaseTransfer", "DOCREL")) {
    docs.addAll(transfer.findByXRef("Document", "TRDOC"));
  }
  _document.generateTemplate(["Case": _document.case, "Document_List": docs]);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}

//If NTC364 is added, then Generate NTC364 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC364") && !_document.stored) {
  def transfer = _document.findByXRef("CaseTransfer", "DOCREL").first();
  _document.generateTemplate(["Case": _document.case, "Case_Transfer": transfer]);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}

//If RUL012 Notice of Decision - Administrative Appeal is generated and keywords AFFIRMED or REVERSED and On then dispose the case
def caseInitDocNumbers = ['COM100', 'COM090'];
if (_document.docNumber.equalsIgnoreCase("RUL012") && _document.case.collect("documents[#p1.contains(docNumber) and la_caseInitDoc]", caseInitDocNumbers).notEmpty && (_document.kv("AFFIRMED") == "On" || _document.kv("REVERSED") == "On")) {
  setCaseDisposition(_document.case, '3400', _document.statusDate);
}

//If NTC375 is added, then Generate NTC375 and print
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC375") && !_document.stored) {
  def params = [:];
  params.put("Case", _document.case);
  params.put("mailingDate", DateUtil.getToday())
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  logger.debug(event);
  if (event) {
    params.put("Event", event);
    if (event.la_eventDocAmend == true) {
      params.put("NTC375_Name", "NOTICE OF UNLAWFUL DETAINER TRIAL - AMENDED");
    } else {
      params.put("NTC375_Name", "NOTICE OF UNLAWFUL DETAINER TRIAL");
    }
    params.put("Event_possession", event.la_eventDocPossession);
  }
  def mailingList = _document.case.collect("la_mailingList[(partyType != 'DEF') or (partyType == 'DEF' and (status == 'RES' or status == 'ANS'))]");
  params.put("CasePerson", mailingList)
  _document.generateTemplate(params);
  contextAccessor.getContext().addMessage(new PrintDocumentMessage(_document, mailingList.size() + 1));
  _document.la_configMemo = null;
}

//If NTC370 is added, then Generate NTC370 and print
logger.debug("NTC370");
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC370") && !_document.stored) {
  def params = [:];
  params.put("Case", _document.case);
  params.put("mailingDate", DateUtil.getToday())
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = null;
  def events = _document.getHearingsByType('DOCREL');
  if(event){
    if(CollectionUtil.isNotEmpty(events)) {
      params.put("Event",CollectionUtil.first(events));
    }
    params.put("date1", DateUtil.formatDateShort(DateUtil.getToday()));
    _document.generateTemplate(params);
    contextAccessor.getContext().addMessage(new PrintDocumentMessage(_document, _document.case.la_mailingList.size() + 1));
    _document.la_configMemo = null;
  }
}

//If DAL010 - Notice of Stay of Proceedings and Early Evaluation Conference (Construction-Related Accessibility Claim) is filed
//Create a case special status PES - Stay - Proceedings/Court of Equal Standing for 90 days
if (_document.docNumber.equalsIgnoreCase("DAL010")) {
  if (_document.collect("case.specialStatuses[status=='CAC' and activeNow==true]").empty) {
    specialStatus = new CaseSpecialStatus();
    endDate = DateUtil.addDays(_document.statusDate, 90)
    specialStatus.startDate = _document.statusDate;
    specialStatus.status = 'CAC';
    specialStatus.endDate = endDate;
    specialStatus.case = _document.case;
    specialStatus.memo = "Stay - Construction Related Accessibility Claim";
    _document.case.specialStatuses.add(specialStatus);
    specialStatus.saveOrUpdateAndExecuteEntityRules(true, false);
  }
}

//If PJ120 is denied then automatically generate ORD050 - Commenting this our per LASCFAM-1794
//if (_document.docNumber == "PJ120" && _document.dispositionType == "DEN" && _document.filedBy.size() > 0 && _document.la_joCode != null) {
//  runRuleInCurrentContext("DOCUMENT_AUTO_GENERATE_ORD050", ["document": _document]);
//}

//Make the filed by party from served to named when MTN610 - Motion to Quash Service of Summons is granted
if (_document.docNumber == "MTN610" && _document.dispositionType == "GRA") {
  def parties = _document.collect("filedBy[status == 'SERV' or status == 'RES']")
  for (party in parties) {
    party.status = "NAMED";
    party.saveOrUpdate();
    partyStatuses = party.collect("la_CivilPartyStatuses[dateRemoved == null]");
    for (partyStatus in partyStatuses) {
      partyStatus.dateRemoved = new Date();
      partyStatus.saveOrUpdate();
    }
  }
}

/*//If fee waiver is denied or granted in part, then void the original payment made with waived court
logger.debug("Begin fee waiver voiding" );
logger.debug("document case: " + _document.case);
logger.debug("document case parties: " + _document.case.collect("parties"));
logger.debug("document case parties receipts: " + _document.case.collect("parties.receipts"));
for(receipt in _document.case.collect("parties.receipts")){
logger.debug("receipt:" + receipt.collect("nonMonetarys"));
if(receipt.collect("nonMonetarys.nonMonetarySetup.nonMonetaryType == 'FEEWAIVER'").size() > 0){
logger.debug("voiding receipt: " + receipt.title);
financialManager.voidReceipt(receipt, receipt.till, '', '');
}
}*/
if(StringUtils.isNotBlank(_document.la_crsReceiptNumber)) {
  receipt = null
  //Check if CRS Receipt exists on this case
  receipts = DomainObject.find(Receipt.class, "receiptNumber", _document.la_crsReceiptNumber, "settled", null)
  for (r in receipts) {
    cse = r.collect("cases[id==${_document.case.id}]").first()
    if (cse != null) receipt = r
    logger.debug("receipt: ${receipt}")
  }
  invoice = receipt.collect("paymentInvoices.invoice").first()
  documentFiledBys = _document.collect("documentParties[type == 'FILEDBY'].party")
  if (receipt && invoice) {
    for(docParty in documentFiledBys){
      if(docParty != invoice.party){
        logger.debug("current: First appearance paid" + docParty.person.firstAppearancePaid)
        docParty.person.firstAppearancePaid = true
        logger.debug("new: First appearance paid" + docParty.person.firstAppearancePaid)
      }
    }
  }
}
//If Document Voided check if 1st Paper related to doc , and if yes update party fee status
//This is temporary. Will check the code of assessments after analysing the impact
feeCodes = ["Limited Civil Answer, including UD (up to \$10K)-GC70614(b),70602.5", "Unlimited Civil- Compt/UD/Pet filed >25k -GC70611,70602.5,70602.6", "Limited Civil Answer (\$10K up to \$25k)-GC 70614(a), 70602.5", "Limited Response \$5k: Civil Answer Oth - B&P 6322.1(c );GC70614(b)", "Limited Civil Answer, including UD (up to \$10K)-GC70614(b),70602.5", "Limited Civil Complaint - (\$10K up to \$25K)-GC 70613(a), 70602.5"];
if (_revertPartyStatusDocDispo.contains(_document.dispositionType)) {
  fees = _document.collect("fees.invoiceAssessments.assessment.code");
  revert = false;
  for (fee in fees) {
    if (feeCodes.contains(fee)) {
      revert = true;
    }
  }
  if (revert) {
    for (party in _document.filedBy) {
      //throw new Exception("why?");
      party.person.firstAppearancePaid = false;
      party.saveOrUpdate();
    }
  }
}

//set name ext for summons as the subcase name so it's known for which document the summons are filed
if (_document.docNumber == 'ISS030' && !_document.nameExtension && !_document.nameExact) {
  def nameExt = 'on '
  // changed for LASCGL-265. JK 2/20/19
  if (_document.subCase.collect("documents").first()?.intendedToAmend) {
    nameExt += 'Amended '
  }
  nameExt += _document.subCase.filingTypeLabel;
  if (!_document.subCase.amendings.empty) {
    nameExt += " (" + _document.subCase.amendings.size() + NumberUtil.getOrdinalFor(_document.subCase.amendings.size()) + ")";
  }
  _document.nameExtension = nameExt;
}

if (_document.docDefNumber == "COM040" && !_document.nameExtension && !_document.nameExact) {
  if (!_document.subCase.amendings.empty) {
    _document.nameExtension = " (" + _document.subCase.amendings.size() + NumberUtil.getOrdinalFor(_document.subCase.amendings.size()) + ")";
  }
}

if (_document.docNumber == 'MTN400') {
  _document.filedByType = 'ATT';
}

//If the Case Initiating document is disposed as one of the values in revertPartyStatusDocDispo update the subcase disposition to 2100 - Court-Ordered Dismissal - Other (Case Voided) and run the subCase disposition rule
//IR546286 Charlton added on 9/8/2020
//if (_document.la_caseInitDoc && _revertPartyStatusDocDispo.contains(_document.dispositionType) && _document.subCase.dispositionType == null) {
if (_document.la_caseInitDoc && _revertPartyStatusDocDispo.contains(_document.dispositionType) && (_document.subCase.dispositionType == null || _document.subCase.dispositionType == '2100' || _document.subCase.dispositionType == '1300VSC' )) {
  def docSubCase = _document.subCase;
  addWarning("This action will dispose the ${docSubCase.displayName}");
  //docSubCase.dispositionType = '2100';
  docSubCase.dispositionType = _document.subCase.dispositionType;
  docSubCase.dispositionDate = _document.dispositionDate;
  docSubCase.saveOrUpdate();
  logger.debug("docSubCase.dispositionType:" + docSubCase.dispositionType)
  if (_document.case.dispositionType == null) {
    runRuleInCurrentContext("CASE_DISPOSITION", ["subCase": docSubCase]);
  }
}

if (_document.case.assignedTrack == 'COL') {
  //If RES010 - Answer is filed on a collection case update the hearing HRG100 - OSC - Failure to File Proof of Service and Failure to File Default Judgment Pursuant to CRC 3.740 to TRL010 - Non-Jury Trial
  def oscHearingType = 'HRG100'
  def oscHearings = _document.case.collect("hearings[type == #p1]", oscHearingType).filter("Event is future and no result");
  if (_document.docNumber == 'RES010') {
    for (hearing in oscHearings) {
      hearing.type = 'TRL010';
      hearing.saveOrUpdate();
    }
  }
  //If NTC341 - Notice of Settlement (Collection Case) is filed, vacate existing HRG100 - OSC - Failure to File Proof of Service and Failure to File Default Judgment Pursuant to CRC 3.740 and create new ones 60 days out from the doc status date and send notice
  if (_document.docNumber == 'NTC341') {
    for (hearing in oscHearings) {
      hearing.eventStatus = "CANCELED";
      hearing.resultType = "VAC";
      hearing.resultDate = new Date();
      hearing.saveOrUpdate();
    }
    hearing = new ScheduledEvent();
    hearing.type = oscHearingType;
    hearing.startDateTime = DateUtil.combine(DateUtil.add(_document.statusDate, "60b_"), DateUtil.parse("8:30 AM"));
    hearing.endDateTime = hearing.startDateTime;
    def caseLocation = _document.case.collect("assignments[dateRemoved == null].dirLocation").first;
    if (caseLocation) {
      hearing.eventLocation = caseLocation;
    }
    _document.case.add(hearing, "hearings");
    hearing.saveOrUpdateAndExecuteEntityRules(true, false);
  }
}

//When REQ150 Request / Counter Request to Set Case for Trial is set and Hearing is added at the same time update the cross refernce to DOCREL instead of CDOC --- this is exception to the rule.
if (_document.docNumber == 'REQ150') {
  for (xref in _document.getDocumentHearingsByType("CDOC")) {
    xref.type = 'DOCREL';
    xref.saveOrUpdate();
  }
}

//If JUD015 is update from a screen, and result is now Filed but there is no result, warn that no disposition will occur
if (_document.docNumber == 'JUD015' && _document.accessContext.isUpdateScreen() && _document.dispositionType == null && ['F', 'SF'].contains(_document.status) && _document.getChangedPropertyValues().containsKey("status")) {
  addWarning("Document status is being changed to ${_document.statusLabel}, but no result type has been selected, so the parties/case will not be disposed.");
}

//When JUD015 or JUD048 is added get the relation with the judgment award and add the document to judgmentAward.documents collection
if (['JUD015', 'JUD048', 'NTC200'].contains(_document.docNumber)) {
  runRuleInCurrentContext("DOCUMENT_GET_JUDGMENT_AWARD", ["document": _document]);  
}

//set static filed by and refers to
runRuleInCurrentContext('DOC_SET_FILED_BY_AND_REFERS_TO_TEXT', ['document':_document]);

//If TRL063 - Request for Statement of Discision is filed insert the La_RequestForDecision Entity
if (_document.docNumber == 'TRL063' && _document.case.collect("la_RequestForDecisions[dateCompleted == null]").empty) {
  Case cse = _document.case;
  La_RequestForDecision request = new La_RequestForDecision();
  def joCode = _document.case.collect("assignments[dateRemoved == null].joCode").first;
  def judge = null;
  if (joCode != null) {
    judge = DirPerson.getByCode(joCode);
  }
  if (judge != null) {
    request.official = judge;
  }
  request.dateStarted = _document.statusDate;
  request.case = cse;
  cse.la_RequestForDecisions.add(request);
  request.saveOrUpdate();
}

//IF NTC095
if (_document.la_configMemo == "AUTO_GEN" && _document.docNumber.equalsIgnoreCase("NTC095") && !_document.stored) {
  def joCode = _document.case.collect("currentAssignment.joCode").first;
  //if case assignment is not found try to get the jo code from the rescheduled to event
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  if (joCode == null) {
    joCode = event?.rescheduledTo?.la_rescheduleToJOCode;
  }

  def params = [:];
  params.put("Case", _document.case);
  params.put("mailingDate", DateUtil.getToday())
  params.put("Event", event);
  _document.generateTemplate(params);
  addPrintMessage(_document);
  _document.la_configMemo = null;
}
//If NTC135 is added, then Generate NTC135
if (_document.docNumber.equalsIgnoreCase("NTC135") && !_document.stored && _document.la_configMemo == "AUTO_GEN") {
  def joCode = _document.case.collect("currentAssignment.joCode").first;
  //if case assignment is not found try to get the jo code from the rescheduled to event
  //def event = _document.findByXRef("ScheduledEvent","DOCREL").first;
  def event = CollectionUtil.first(_document.getHearingsByType('DOCREL'));
  if (joCode == null) {
    joCode = event?.rescheduledTo?.la_rescheduleToJOCode;
  }
  if (joCode != null && DirPerson.getByCode(joCode)?.isAttachmentAvailable('SIG')) {
    def params = [:];
    params.put("Case", _document.case);
    params.put("mailingDate", DateUtil.getToday())
    params.put("Event", event);
    params.put("CommonManual_joSignature", joCode);
    _document.generateTemplate(params);
    addPrintMessage(_document);
    _document.la_configMemo = null;
  } else {
    addError("Must assign Judicial Officer with signature on file to case " + _document.case.caseNumber + " before rescheduling.");
    logger.debug('throw error');
  }
}

//Commenting this out per ticket CU-816
//If a proposed judgment becomes entered from the Judge/JA WQ then automatically generate the notice of entry of judgment
//if (['JUD015', 'JUD048'].contains(_document.docNumber) && _document.dispositionType == 'GRA' && ['F', 'SF'].contains(_document.status)) {
//  if (_document.judgmentAwards != null && _document.judgmentAwards.size() > 0) {
//    runRuleInCurrentContext("DOCUMENT_GENERATE_JUDGMENT_NOTICE", ["document": _document]);
//  }
//}

if (_document.dispositionType == 'CAN') {
  DomainObject.updateHql("update ROAMessage set la_deleted=true, la_changeDate=:now where recordEntityName=:entity and recordId=:id",
                         "entity", _document.entity.name, "id", _document.id, "now", new Date());
}


if (_document.docNumber == 'RUL015') {
  //for(doc in _document.crossReferencedDocuments){
  for (doc in _document.relatedDocuments) {
    if (doc.isStored()) {
      doc.textStamp("SEE NUNC PRO TUNC ORDER OF " + DateUtil.formatDateShort(_document.statusDate), 90, 20, 200, 10, null, null, null);
    }
  }
}

if (_document.dispositionType == 'CAN' && !_document.transfers.empty) {
  // assume this document got transfered
  _document.la_fileNetIndexed = null;
  _document.fileNetId = null;
  _document.la_fnStorageId = null;
}

//Create alert if 'CHL020 Challenge to Judicial Officer - Peremptory' is entered / Discard flags if type is changed
if (_document.docNumber == 'CHL020') {
  def suffix = null;
  if (_document.collect("filedBy[{'PLAIN', 'PET', 'XCMPLNT', 'ASSIGN', 'RPIN', 'INTVEN', 'CPLAIN', 'CNST', 'INTXC', 'REALPARTYINT'}.contains(partyType)]").size() > 0) {
    suffix = '_P';
  } else if (_document.collect("filedBy[{'DEF', 'RES', 'ARRCLM', 'XDEF', 'CLAIM', 'CDEF', 'INTDEF', 'INTXD', 'XRES'}.contains(partyType)]").size() > 0) {
    suffix = '_D';
  } else if (_document.collect("filedBy[{'TPTY'}.contains(partyType)]").size() > 0) {
    suffix = '_N';
  }

  if (suffix) {
    def alertType170 = '170' + suffix;
    def otherTypes170 = ['170_P','170_D','170_N' ];
    otherTypes170.remove(alertType170);

    //If no 170 flag exists yet for this suffix, add it.
    if (_document.case.collect("alerts[alertType=='${alertType170}' && source=='${_document.getEntityShortNameAndId()}']").isEmpty()) {
      def alert170 = _case.addAlert(alertType170, _document);
      alert170.alertStyle = 'alert';
    }

    //If there were any 170 flags for the other suffix, remove them (this occurs if the user changes the Filed By, Plaintiff <--> Defendant)
    for (otherAlertType170 in _document.case.collect("alerts[#p1.contains(alertType) && source=='${_document.getEntityShortNameAndId()}']", otherTypes170)) {
      otherAlertType170.deleteRemoveFromPeers();
    }
  }//Filed by unexpected party
} else {
  if (_document.case.collect("alerts[alertType.startsWith('170_') && source=='${_document.getEntityShortNameAndId()}']").size() > 0) {
    //There are 170.6 alerts for this doc, but the docDef is not CHL020. We can assume that the DocDef is being changed.
    if (_document.dispositionType == null) {
      addWarning("The 170.6 flag related to this document will be discarded. Are you sure you want to change the document's Code/Name? If so, click 'Save' again.");
      //Drop 170.6 alerts linked to this doc that is no longer a CHL020
      _document.case.dropAlerts("170_L", _document)
      _document.case.dropAlerts("170_D", _document)
      _document.case.dropAlerts("170_P", _document)
      _document.case.dropAlerts("170_N", _document)
    } else {
      addError("The Document's Code/Name cannot be modified, because a decision was already made about the 'Challenge To Judicial Officer - Peremptory (170.6)'.");
    }
  }//else, there are no alerts, so no need to drop them.
}

def setCaseDisposition(Case cse, String dispoType, Date dispoDate) {
  logger.debug("deleting active disposition entries");
  for (disposition in cse.collect("dispositions[reopenReasonDate==null]")) {
    cse.dispositions.remove(disposition);
    disposition.case = null;
    disposition.delete();
  }

  logger.debug("** set case disposition to ${dispoType} on ${dispoDate}");
  cse.dispositionType = dispoType;
  cse.dispositionDate = dispoDate;

  cse.status = 'DIS';
  disposition = new CaseDisposition();
  disposition.dispositionDate = dispoDate;
  disposition.dispositionType = dispoType;
  cse.add(disposition, "dispositions");
  cse.saveOrUpdate();
}

//If a JUD015 or JUD048 is added and related to the judgment add the judgment details in nameExtension
if (['JUD015', 'JUD048'].contains(_document.docNumber) && _document.nameExtension == null && (!_enforcementCategory.contains(_document.case.category)) && _document.judgmentAwards != null && _document.judgmentAwards.size() > 0) {
  def judgmentNameExt = [];
  for (judgmentAward in _document.judgmentAwards) {
    judgmentNameExt.add(judgmentAward.dispositionTypeLabel + ' - ' + judgmentAward.la_forAndAgainstParties)
  }
  def judNameExt = StringUtils.join(judgmentNameExt, ";");
  if (StringUtils.isNotBlank(judNameExt)) {
    _document.nameExtension = StringUtils.ellipse('- ' + StringUtils.join(judgmentNameExt, ";"), 255);
  }
  logger.debug("_document.nameExtension:" + _document.nameExtension);
}

//Check for Filed By Changed and Doc is a fee doc then throw warning
if (_document.fees.size() > 0 && _document.la_filedByChanged != null) {
  addWarning("You are changing the Filed By on a document which may have fees associated to it. Please make sure you handle the fees separately");
}

runRuleInCurrentContext('DOC_SET_PROPS_WQ_STATUS', ['document':_document]);

if (_document.docNumber == 'REQ030') {
  runRule("DOC_SET_PROPS_REQ030", ["document": _document]);  
}















