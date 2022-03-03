package org.orcid.frontend.web.controllers;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.orcid.core.constants.EmailConstants;
import org.orcid.core.manager.*;
import org.orcid.core.manager.v3.AddressManager;
import org.orcid.core.manager.v3.BiographyManager;
import org.orcid.core.manager.v3.EmailManager;
import org.orcid.core.manager.v3.GivenPermissionToManager;
import org.orcid.core.manager.v3.NotificationManager;
import org.orcid.core.manager.v3.ProfileEntityManager;
import org.orcid.core.manager.v3.RecordNameManager;
import org.orcid.core.manager.v3.read_only.EmailManagerReadOnly;
import org.orcid.core.manager.v3.read_only.GivenPermissionToManagerReadOnly;
import org.orcid.core.manager.v3.read_only.ProfileEntityManagerReadOnly;
import org.orcid.core.manager.v3.read_only.RecordNameManagerReadOnly;
import org.orcid.core.utils.JsonUtils;
import org.orcid.core.utils.v3.OrcidIdentifierUtils;
import org.orcid.frontend.web.util.CommonPasswords;
import org.orcid.jaxb.model.v3.release.record.Addresses;
import org.orcid.jaxb.model.v3.release.record.Biography;
import org.orcid.jaxb.model.v3.release.record.Emails;
import org.orcid.jaxb.model.v3.release.record.Name;
import org.orcid.frontend.web.util.PasswordConstants;
import org.orcid.persistence.jpa.entities.EmailEntity;
import org.orcid.persistence.jpa.entities.ProfileEntity;
import org.orcid.persistence.jpa.entities.UserconnectionEntity;
import org.orcid.pojo.AddEmail;
import org.orcid.pojo.ApplicationSummary;
import org.orcid.pojo.ChangePassword;
import org.orcid.pojo.DelegateForm;
import org.orcid.pojo.DeprecateProfile;
import org.orcid.pojo.EmailFrequencyOptions;
import org.orcid.pojo.ManageDelegate;
import org.orcid.pojo.ManageSocialAccount;
import org.orcid.pojo.ajaxForm.AddressForm;
import org.orcid.pojo.ajaxForm.AddressesForm;
import org.orcid.pojo.ajaxForm.BiographyForm;
import org.orcid.pojo.ajaxForm.EditEmail;
import org.orcid.pojo.ajaxForm.Email;
import org.orcid.pojo.ajaxForm.Errors;
import org.orcid.pojo.ajaxForm.NamesForm;
import org.orcid.pojo.ajaxForm.PojoUtil;
import org.orcid.pojo.ajaxForm.Text;
import org.orcid.pojo.ajaxForm.Visibility;
import org.orcid.utils.OrcidStringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Declan Newman (declan) Date: 22/02/2012
 */
@Controller("manageProfileController")
@RequestMapping(value = { "/account", "/manage" })
public class ManageProfileController extends BaseWorkspaceController {

    private static final String IS_SELF = "isSelf";

    private static final String FOUND = "found";   
    
    @Resource
    private EncryptionManager encryptionManager;

    @Resource(name = "notificationManagerV3")
    private NotificationManager notificationManager;

    @Resource(name = "profileEntityManagerV3")
    private ProfileEntityManager profileEntityManager;

    @Resource(name = "profileEntityManagerReadOnlyV3")
    private ProfileEntityManagerReadOnly profileEntityManagerReadOnly;
    
    @Resource
    private GivenPermissionToManager givenPermissionToManager;

    @Resource(name = "emailManagerV3")
    private EmailManager emailManager;

    @Resource(name = "emailManagerReadOnlyV3")
    private EmailManagerReadOnly emailManagerReadOnly;
    
    @Resource
    private UserConnectionManager userConnectionManager;

    @Resource
    private ProfileEntityCacheManager profileEntityCacheManager;

    @Resource(name = "addressManagerV3")
    private AddressManager addressManager;

    @Resource(name = "biographyManagerV3")
    private BiographyManager biographyManager;
    
    @Resource(name = "recordNameManagerV3")
    private RecordNameManager recordNameManager;
    
    @Resource(name = "recordNameManagerReadOnlyV3")
    private RecordNameManagerReadOnly recordNameManagerReadOnlyV3;
    
    @Resource
    private PreferenceManager preferenceManager;
    
    @Resource
    private OrcidIdentifierUtils orcidIdentifierUtils;
    
    @Resource
    private GivenPermissionToManagerReadOnly givenPermissionToManagerReadOnly;

    @Resource
    private TwoFactorAuthenticationManager twoFactorAuthenticationManager;
    
    @RequestMapping
    public ModelAndView manageProfile() {
        return new ModelAndView("manage");
    }
    
    @RequestMapping(value = "/search-for-delegate-by-email/{email}/")
    public @ResponseBody Map<String, Boolean> searchForDelegateByEmail(@PathVariable String email) {
        Map<String, Boolean> map = new HashMap<>();
        EmailEntity emailEntity = emailManager.find(email);
        if (emailEntity == null) {
            map.put(FOUND, Boolean.FALSE);
            return map;
        } else {
            map.put(FOUND, Boolean.TRUE);
            map.put(IS_SELF, emailEntity.getProfile().getId().equals(getCurrentUserOrcid()));
            return map;
        }
    }    
    
    @RequestMapping(value = "/search-for-delegate-by-orcid/{orcid}/")
    public @ResponseBody Map<String, Boolean> searchForDelegateByOrcid(@PathVariable String orcid) {
        Map<String, Boolean> map = new HashMap<>();
        Boolean isValidForDelegate = profileEntityManagerReadOnly.isOrcidValidAsDelegate(orcid);
        if (isValidForDelegate == null || isValidForDelegate.booleanValue() == false) {
            map.put(FOUND, Boolean.FALSE);
            return map;
        } else {
            map.put(FOUND, Boolean.TRUE);
            map.put(IS_SELF, orcid.equals(getCurrentUserOrcid()));
            return map;
        }
    } 

    @RequestMapping(value = "/delegates.json", method = RequestMethod.GET)
    public @ResponseBody List<DelegateForm>  getDelegates() {
        String currentOrcid = getCurrentUserOrcid();
        return givenPermissionToManagerReadOnly.findByGiver(currentOrcid, getLastModified(currentOrcid));
    }
    
    @RequestMapping(value = "/addDelegate.json")
    public @ResponseBody ManageDelegate addDelegate(@RequestBody ManageDelegate addDelegate) {
        // Check password
        String password = addDelegate.getPassword();
        ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        if (orcidSecurityManager.isPasswordConfirmationRequired()
                && (StringUtils.isBlank(password) || !encryptionManager.hashMatches(password, profile.getEncryptedPassword()))) {
            addDelegate.getErrors().add(getMessage("check_password_modal.incorrect_password"));
            return addDelegate;
        }
        
        givenPermissionToManager.create(getCurrentUserOrcid(), addDelegate.getDelegateToManage());
                                    
        return addDelegate;
    }

    @RequestMapping(value = "/addDelegateByEmail.json")
    public @ResponseBody ManageDelegate addDelegateByEmail(@RequestBody ManageDelegate addDelegate) {
        EmailEntity emailEntity = emailManager.find(addDelegate.getDelegateEmail());
        addDelegate.setDelegateToManage(emailEntity.getProfile().getId());
        return addDelegate(addDelegate);
    }

    @RequestMapping(value = "/addDelegateByOrcid.json")
    public @ResponseBody ManageDelegate addDelegateByOrcid(@RequestBody ManageDelegate addDelegate) {
        addDelegate.setDelegateToManage(addDelegate.getDelegateToManage());
        return addDelegate(addDelegate);
    }
    
    @RequestMapping(value = "/revokeDelegate.json", method = RequestMethod.POST)
    public @ResponseBody ManageDelegate revokeDelegate(@RequestBody ManageDelegate manageDelegate) {
        // Check password
        String password = manageDelegate.getPassword();
        ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        if (orcidSecurityManager.isPasswordConfirmationRequired()
                && (StringUtils.isBlank(password) || !encryptionManager.hashMatches(password, profile.getEncryptedPassword()))) {
            manageDelegate.getErrors().add(getMessage("check_password_modal.incorrect_password"));
            return manageDelegate;
        }               
        givenPermissionToManager.remove(getCurrentUserOrcid(), manageDelegate.getDelegateToManage());
        return manageDelegate;
    }

    @RequestMapping(value = "/socialAccounts.json", method = RequestMethod.GET)
    public @ResponseBody List<UserconnectionEntity> getSocialAccountsJson(HttpServletRequest request) {
        String orcid = getCurrentUserOrcid();
        List<UserconnectionEntity> userConnectionEntities = userConnectionManager.findByOrcid(orcid);
        return userConnectionEntities;
    }

    @RequestMapping(value = "/revokeSocialAccount.json", method = RequestMethod.POST)
    public @ResponseBody ManageSocialAccount revokeSocialAccount(@RequestBody ManageSocialAccount manageSocialAccount) {
        // Check password
        String password = manageSocialAccount.getPassword();
        ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        if (orcidSecurityManager.isPasswordConfirmationRequired()
                && (StringUtils.isBlank(password) || !encryptionManager.hashMatches(password, profile.getEncryptedPassword()))) {
            manageSocialAccount.getErrors().add(getMessage("check_password_modal.incorrect_password"));
            return manageSocialAccount;
        }
        userConnectionManager.remove(getEffectiveUserOrcid(), manageSocialAccount.getIdToManage());
        return manageSocialAccount;
    }

    @RequestMapping(value = "/revoke-application.json", method = RequestMethod.POST)
    public @ResponseBody boolean revokeApplication(@RequestParam("clientId") String clientId) {
        profileEntityManager.disableClientAccess(clientId, getCurrentUserOrcid());
        return true;
    }

    @RequestMapping(value = "/preferences.json", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> getDefaultPreference(HttpServletRequest request) {
        Map<String, Object> preferences = new HashMap<String, Object>();
        
        ProfileEntity entity = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        preferences.put("default_visibility", entity.getActivitiesVisibilityDefault());
        preferences.put("developer_tools_enabled", entity.getEnableDeveloperTools());
        return preferences;
    }
    
    @RequestMapping(value = "/default_visibility.json", method = RequestMethod.POST)
    public @ResponseBody String setDefaultVisibility(@RequestBody String defaultVisibility) throws IllegalArgumentException {
        try {
            org.orcid.jaxb.model.common_v2.Visibility visibility = org.orcid.jaxb.model.common_v2.Visibility.fromValue(defaultVisibility); 
            if(org.orcid.jaxb.model.common_v2.Visibility.REGISTERED_ONLY.equals(visibility) || org.orcid.jaxb.model.common_v2.Visibility.SYSTEM.equals(visibility)) {
                throw new IllegalArgumentException();
            }
            preferenceManager.updateDefaultVisibility(getCurrentUserOrcid(), visibility);
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid visibility provided: " + defaultVisibility);
        }
        
        return "{\"status\": \"" + defaultVisibility + "\"}";
    }
    
    @RequestMapping(value = { "/change-password.json" }, method = RequestMethod.GET)
    public @ResponseBody ChangePassword getChangedPasswordJson(HttpServletRequest request) {
        return new ChangePassword();        
    }

    @RequestMapping(value = { "/change-password.json" }, method = RequestMethod.POST)
    public @ResponseBody ChangePassword changedPasswordJson(HttpServletRequest request, @RequestBody ChangePassword cp) {
        List<String> errors = new ArrayList<String>();
        ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        
        if (cp.getPassword() == null || !cp.getPassword().matches(PasswordConstants.ORCID_PASSWORD_REGEX)) {
            errors.add(getMessage("Pattern.registrationForm.passwordRequirement"));
        } else if (!cp.getPassword().equals(cp.getRetypedPassword())) {
            errors.add(getMessage("FieldMatch.registrationForm"));
        } 

        if (CommonPasswords.passwordIsCommon(cp.getPassword())) {
            errors.add(getMessage("password.too_common", cp.getPassword()));
        }

        if (cp.getOldPassword() == null || !encryptionManager.hashMatches(cp.getOldPassword(), profile.getEncryptedPassword())) {
            errors.add(getMessage("orcid.frontend.change.password.current_password_incorrect"));
        }
        if (cp.getPassword() != null && !cp.getPassword().isEmpty()) {
        	final String newPassword  = cp.getPassword();
        	 emailManager.getEmails(getCurrentUserOrcid()).getEmails().forEach(email -> {
	        	if (!email.getEmail().isEmpty()  && newPassword.contains(email.getEmail())) {
	        		errors.add(getMessage("Pattern.registrationForm.password.containsEmail"));
	        	}
	        	
	        });
        }

        if (errors.size() == 0) {            
            profileEntityManager.updatePassword(getCurrentUserOrcid(), cp.getPassword());
            cp = new ChangePassword();
            errors.add(getMessage("orcid.frontend.change.password.change.successfully"));
        }
        cp.setErrors(errors);
        return cp;
    }

    @RequestMapping(value = "/deprecate-profile.json")
    public @ResponseBody DeprecateProfile getDeprecateProfile() {
        return new DeprecateProfile();
    }

    @RequestMapping(value = "/validate-deprecate-profile.json", method = RequestMethod.POST)
    public @ResponseBody DeprecateProfile validateDeprecateProfile(@RequestBody DeprecateProfile deprecateProfile) {
        validateFormData(deprecateProfile);
        if (!deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }

        String currentUserOrcid = getCurrentUserOrcid();
        ProfileEntity primaryEntity = profileEntityCacheManager.retrieve(currentUserOrcid);
        ProfileEntity deprecatingEntity = getDeprecatingEntity(deprecateProfile);
        
        validateDeprecatingEntity(deprecatingEntity, primaryEntity, deprecateProfile);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }
        
        validateNonDeprecatingEntity(deprecateProfile, primaryEntity);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }

        validateDeprecateAccountRequest(deprecateProfile, deprecatingEntity);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }

        Emails deprecatingEmails = emailManager.getEmails(deprecatingEntity.getId());
        Emails primaryEmails = emailManager.getEmails(primaryEntity.getId());
                
        String primaryOrcid = primaryEntity.getId();
        String deprecatingOrcid = deprecatingEntity.getId();
        
        String primaryAccountName = recordNameManagerReadOnlyV3.fetchDisplayablePublicName(primaryOrcid);
        String deprecatingAccountName = recordNameManagerReadOnlyV3.fetchDisplayablePublicName(deprecatingOrcid);
        deprecateProfile.setPrimaryAccountName(primaryAccountName);
        deprecateProfile.setPrimaryOrcid(currentUserOrcid);
        deprecateProfile.setDeprecatingAccountName(deprecatingAccountName);
        deprecateProfile.setDeprecatingOrcid(deprecatingEntity.getId());

        if (deprecatingEmails != null) {
            deprecateProfile.setDeprecatingEmails(
                    deprecatingEmails.getEmails().stream().map(e -> e.getEmail()).collect(Collectors.toList()));
        }
        if (primaryEmails != null) {
            deprecateProfile.setPrimaryEmails(
                    primaryEmails.getEmails().stream().map(e -> e.getEmail()).collect(Collectors.toList()));
        }

        deprecateProfile.setVerificationCodeRequired(twoFactorAuthenticationManager.userUsing2FA(deprecatingOrcid));

        return deprecateProfile;
    }

    @RequestMapping(value = "/confirm-deprecate-profile.json", method = RequestMethod.POST)
    public @ResponseBody DeprecateProfile confirmDeprecateProfile(@RequestBody DeprecateProfile deprecateProfile) {
        validateFormData(deprecateProfile);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }
        
        ProfileEntity primaryEntity = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        ProfileEntity deprecatingEntity = getDeprecatingEntity(deprecateProfile);
        
        validateDeprecatingEntity(deprecatingEntity, primaryEntity, deprecateProfile);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }

        validateNonDeprecatingEntity(deprecateProfile, primaryEntity);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }
        
        validateDeprecateAccountRequest(deprecateProfile, deprecatingEntity);
        if (deprecateProfile.getErrors() != null && !deprecateProfile.getErrors().isEmpty()) {
            return deprecateProfile;
        }

        boolean deprecated = profileEntityManager.deprecateProfile(deprecatingEntity.getId(), primaryEntity.getId(), ProfileEntity.USER_DRIVEN_DEPRECATION, null);
        if (!deprecated) {
            deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.problem_deprecating")));
        }
        return deprecateProfile;
    }

    private void validateNonDeprecatingEntity(DeprecateProfile deprecateProfile, ProfileEntity entity) {
       if (entity.getDeprecatedDate() != null) {
           deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.this_profile_deprecated", entity.getId())));
       }
       
       if (entity.getDeactivationDate() != null) {
           deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.this_profile_deactivated", entity.getId())));
       }
    }

    private void validateDeprecatingEntity(ProfileEntity deprecatingEntity, ProfileEntity primaryEntity, DeprecateProfile deprecateProfile) {
        if (deprecatingEntity == null) {
            deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.profile_does_not_exist")));
        } else if (primaryEntity.getId().equals(deprecatingEntity.getId())) {
            deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.profile_matches_current")));
        }
    }

    private void validateFormData(DeprecateProfile deprecateProfile) {
        List<String> errors = new ArrayList<>();
        if (deprecateProfile.getDeprecatingPassword() == null || deprecateProfile.getDeprecatingPassword().trim().isEmpty()) {
            errors.add(getMessage("deprecate_orcid.no_password_specified"));
        }
        if (deprecateProfile.getDeprecatingOrcidOrEmail() == null) {
            errors.add(getMessage("deprecate_orcid.no_profile_specified"));
        }
        deprecateProfile.setErrors(errors);
    }

    private void validateDeprecateAccountRequest(DeprecateProfile deprecateProfile, ProfileEntity deprecatingEntity) {
        if (!encryptionManager.hashMatches(deprecateProfile.getDeprecatingPassword(), deprecatingEntity.getEncryptedPassword())) {
            deprecateProfile.setErrors(Arrays.asList(getMessage("check_password_modal.incorrect_password")));
        } else if (deprecatingEntity.getDeprecatedDate() != null) {
            deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.already_deprecated", deprecatingEntity.getId())));
        } else if (deprecatingEntity.getDeactivationDate() != null) {
            deprecateProfile.setErrors(Arrays.asList(getMessage("deprecate_orcid.already_deactivated", deprecatingEntity.getId())));
        }
    }

    private ProfileEntity getDeprecatingEntity(DeprecateProfile deprecateProfile) {
        String orcidIdOrEmail = deprecateProfile.getDeprecatingOrcidOrEmail().trim();
        if (deprecateProfile.getDeprecatingOrcidOrEmail().contains("@")) {
            EmailEntity emailEntity = emailManager.find(orcidIdOrEmail);
            if (emailEntity != null) {
                return emailEntity.getProfile();
            }
        } else {
            if (OrcidStringUtils.getOrcidNumber(orcidIdOrEmail) != null && OrcidStringUtils.isValidOrcid(OrcidStringUtils.getOrcidNumber(orcidIdOrEmail))) {
                try {
                    ProfileEntity profileEntity = profileEntityCacheManager.retrieve(OrcidStringUtils.getOrcidNumber(orcidIdOrEmail));
                    if (profileEntity != null) {
                        return profileEntity;
                    }
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    @RequestMapping(value = { "deactivate-orcid", "/view-deactivate-orcid-account" }, method = RequestMethod.GET)
    public ModelAndView viewDeactivateOrcidAccount() {
        return new ModelAndView("deactivate_orcid");
    }

    @RequestMapping(value = "/confirm-deactivate-orcid/{encryptedEmail}", method = RequestMethod.GET)
    public ModelAndView confirmDeactivateOrcidAccount(HttpServletRequest request, HttpServletResponse response, @PathVariable("encryptedEmail") String encryptedEmail,
            RedirectAttributes redirectAttributes) throws Exception {
        ModelAndView result = null;
        String decryptedEmail = encryptionManager.decryptForExternalUse(new String(Base64.decodeBase64(encryptedEmail), "UTF-8"));
        String primaryEmail = emailManager.findPrimaryEmail(getCurrentUserOrcid()).getEmail();

        if (decryptedEmail.equals(primaryEmail)) {
            profileEntityManager.deactivateRecord(getCurrentUserOrcid());
            logoutCurrentUser(request, response);
            result = new ModelAndView("redirect:/signin#deactivated");
        } else {
            redirectAttributes.addFlashAttribute("emailDoesntMatch", true);
            return new ModelAndView("redirect:/my-orcid");
        }

        return result;
    }
    
    @RequestMapping(value = "/verifyEmail.json", method = RequestMethod.GET)
    public @ResponseBody Errors verifyEmail(HttpServletRequest request, @RequestParam("email") String email) {  
    	String currentUserOrcid = getCurrentUserOrcid();
        String primaryEmail = emailManager.findPrimaryEmail(currentUserOrcid).getEmail();
        if (primaryEmail.equals(email))
            request.getSession().setAttribute(EmailConstants.CHECK_EMAIL_VALIDATED, false);

        String emailOwner = emailManagerReadOnly.findOrcidIdByEmail(email);
        if(!currentUserOrcid.equals(emailOwner)) {
        	throw new IllegalArgumentException("Invalid email address provided");
        }
        
        notificationManager.sendVerificationEmail(currentUserOrcid, email);
        return new Errors();
    }

    @RequestMapping(value = "/delayVerifyEmail.json", method = RequestMethod.GET)
    public @ResponseBody Errors delayVerifyEmail(HttpServletRequest request) {
        request.getSession().setAttribute(EmailConstants.CHECK_EMAIL_VALIDATED, false);
        return new Errors();
    }

    @RequestMapping(value = "/send-deactivate-account.json", method = RequestMethod.GET)
    public @ResponseBody String startDeactivateOrcidAccount(HttpServletRequest request) {
        String currentUserOrcid = getCurrentUserOrcid();
        notificationManager.sendOrcidDeactivateEmail(currentUserOrcid);
        return emailManager.findPrimaryEmail(currentUserOrcid).getEmail();
    }

    @RequestMapping(value = "/emails.json", method = RequestMethod.GET)
    public @ResponseBody org.orcid.pojo.ajaxForm.Emails getEmails(HttpServletRequest request) {                                
        Emails v2Emails = emailManager.getEmails(getCurrentUserOrcid());       
        return org.orcid.pojo.ajaxForm.Emails.valueOf(v2Emails);
    }

    @RequestMapping(value = "/emails.json", method = RequestMethod.POST)
    public @ResponseBody org.orcid.pojo.ajaxForm.Emails setEmails(HttpServletRequest request,  @RequestBody org.orcid.pojo.ajaxForm.Emails newEmailSet) {                                
        Emails oldEmailSet = emailManager.getEmails(getCurrentUserOrcid());  
        List<org.orcid.jaxb.model.v3.release.record.Email> deletedEmails = new ArrayList<org.orcid.jaxb.model.v3.release.record.Email>();
        List<Email> newEmails = new ArrayList<Email>();
        String orcid = getCurrentUserOrcid();
        List<String> errors = new ArrayList<String>();
        
        for (org.orcid.pojo.ajaxForm.Email newJsonEmail : newEmailSet.getEmails()) {
            boolean isNewEmail = true;
            for (org.orcid.jaxb.model.v3.release.record.Email oldJsonEmail: oldEmailSet.getEmails()) {
                if (newJsonEmail.getValue().equals(oldJsonEmail.getEmail())){
                    isNewEmail = false;
                    // VISIBILITY UPDATE
                    if (!newJsonEmail.getVisibility().value().equals(oldJsonEmail.getVisibility().value())){
                        updateEmailVisibility(newJsonEmail);
                    }
                    // Primary email UPDATE
                    if (newJsonEmail.isPrimary() != null &&  newJsonEmail.isPrimary() && !oldJsonEmail.isPrimary()) {
                        org.orcid.pojo.ajaxForm.Email response  = setPrimary(request, newJsonEmail);
                        errors.addAll(response.getErrors());
                    }
                }
            }
            if (isNewEmail) {
                // List emails to be added
                newEmails.add(newJsonEmail);
            }
        }
        
        for (org.orcid.jaxb.model.v3.release.record.Email oldJsonEmail : oldEmailSet.getEmails()) {
            boolean emailWasDeleted = true;
            for (org.orcid.pojo.ajaxForm.Email  newJsonEmail:   newEmailSet.getEmails()) {
                if (newJsonEmail.getValue().equals(oldJsonEmail.getEmail())){
                    emailWasDeleted = false;
                }
            }
            if (emailWasDeleted) {
                // List emails to be deleted
                deletedEmails.add(oldJsonEmail);
            }
        }
                      
        for (Email newEmail : newEmails) {
            AddEmail newEmailCasted = new org.orcid.pojo.AddEmail();
            newEmailCasted.setCurrent(true);
            newEmailCasted.setValue(newEmail.getValue());
            newEmailCasted.setVisibility(newEmail.getVisibility());
            newEmailCasted.setPrimary(false);
            newEmailCasted.setVerified(false);
            org.orcid.pojo.ajaxForm.Email response  = addEmails ( request, newEmailCasted);
            errors.addAll(response.getErrors());
            if (newEmail.isPrimary() != null &&  newEmail.isPrimary()) {
                org.orcid.pojo.ajaxForm.Email setAsPrimaryResponse  = setPrimary(request, newEmail);
                errors.addAll(setAsPrimaryResponse.getErrors());
            }
            
        }
        
        for (org.orcid.jaxb.model.v3.release.record.Email deletedEmail : deletedEmails) {
            deleteEmailJson ( deletedEmail.getEmail() );            
        }
        
        Emails updatedSet = emailManager.getEmails(getCurrentUserOrcid());
        org.orcid.pojo.ajaxForm.Emails emailsResponse = org.orcid.pojo.ajaxForm.Emails.valueOf(updatedSet);
        emailsResponse.setErrors(errors);
        return emailsResponse;
    }

    @RequestMapping(value = "/addEmail.json", method = RequestMethod.POST)
    public @ResponseBody org.orcid.pojo.ajaxForm.Email addEmails(HttpServletRequest request, @RequestBody org.orcid.pojo.AddEmail email) {
        List<String> errors = new ArrayList<String>();
        ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        // Check password
        if (orcidSecurityManager.isPasswordConfirmationRequired()
                && (email.getPassword() == null || !encryptionManager.hashMatches(email.getPassword(), profile.getEncryptedPassword()))) {
            errors.add(getMessage("check_password_modal.incorrect_password"));
        }

        // if blank
        if (PojoUtil.isEmpty(email.getValue())) {
            errors.add(getMessage("Email.personalInfoForm.email"));
        }
        
        // if > 30
        Emails currentEmails = emailManager.getEmails(getCurrentUserOrcid());
        if(currentEmails.getEmails().size() > (EmailConstants.MAX_EMAIL_COUNT - 1 )) {
            errors.add(getMessage("Email.personalInfoForm.youCannotAddMore1") + " " + EmailConstants.MAX_EMAIL_COUNT + " "  + getMessage("Email.personalInfoForm.youCannotAddMore2"));
        }

        MapBindingResult mbr = new MapBindingResult(new HashMap<String, String>(), "Email");
        // Clean the email address so it doesn't contains any horizontal white spaces
        email.setValue(OrcidStringUtils.filterEmailAddress(email.getValue()));
        
        // make sure there are no dups
        validateEmailAddress(email.getValue(), false, false, request, mbr);

        for (ObjectError oe : mbr.getAllErrors()) {
            if (oe.getCode() != null) {
                errors.add(getMessage(oe.getCode(), oe.getArguments()));
            } else {
                errors.add(oe.getDefaultMessage());
            }
        }
        
        if (errors.isEmpty()) {
            // clear errors
            email.setErrors(new ArrayList<String>());
            String currentUserOrcid = getCurrentUserOrcid();            
            emailManager.addEmail(request, currentUserOrcid, email.toV3Email());                            
        } else {
            email.setErrors(errors);
        }
        return email;
    }
    
    @RequestMapping(value = "/validateEmail.json", method = RequestMethod.POST)
    public @ResponseBody org.orcid.pojo.ajaxForm.Email validatEmail(HttpServletRequest request, @RequestBody org.orcid.pojo.AddEmail email) {
        List<String> errors = new ArrayList<String>();

        MapBindingResult mbr = new MapBindingResult(new HashMap<String, String>(), "Email");
        // Clean the email address so it doesn't contains any horizontal white spaces
        email.setValue(OrcidStringUtils.filterEmailAddress(email.getValue()));
        
        validateEmailAddress(email.getValue(), true, false, request, mbr);
        
        for (ObjectError oe : mbr.getAllErrors()) {
            if (oe.getCode() != null) {
                errors.add(getMessage(oe.getCode(), oe.getArguments()));
            } else {
                errors.add(oe.getDefaultMessage());
            }
        }
        if (!errors.isEmpty()) {
            email.setErrors(errors);
                          
        } 
        return email;

    }

    

    @RequestMapping(value = "/deleteEmail.json", method = RequestMethod.DELETE)
    public @ResponseBody Errors deleteEmailJson(@RequestParam("email") String email) {
        Errors errors = new Errors();
        if (PojoUtil.isEmpty(email)) {
            errors.getErrors().add(getMessage("Email.personalInfoForm.email"));
            return errors;
        }

        String currentUserOrcid = getCurrentUserOrcid();
        String owner = null;

        try {
            owner = emailManagerReadOnly.findOrcidIdByEmail(email);
        } catch (NoResultException nre) {

        }

        if (!currentUserOrcid.equals(owner)) {
            errors.getErrors().add(getMessage("Email.personalInfoForm.email"));
            return errors;
        }

        // Don't allow the user to delete a primary email
        if (emailManager.isPrimaryEmail(currentUserOrcid, email)) {
            errors.getErrors().add(getMessage("manage.email.primaryEmailDeletion"));
            return errors;
        } 
        
        if (emailManager.isUsersOnlyEmail(currentUserOrcid, email)) {
            errors.getErrors().add(getMessage("manage.email.primaryEmailDeletion"));
            return errors;
        } 
        
        emailManager.removeEmail(currentUserOrcid, email);
        return errors;
    }
    
    @RequestMapping(value = "/email/visibility", method = RequestMethod.POST)
    public @ResponseBody org.orcid.pojo.ajaxForm.Email updateEmailVisibility(@RequestBody org.orcid.pojo.ajaxForm.Email email) {
        String orcid = getCurrentUserOrcid();
        String owner = emailManager.findOrcidIdByEmail(email.getValue());
        if(orcid.equals(owner)) {
            if(email.getVisibility() != null) {
                // Updates the visibility on the given email
                emailManager.updateVisibility(orcid, email.getValue(), email.getVisibility());
            }            
        }
        return email;
    }
    
    @RequestMapping(value = "/email/setPrimary", method = RequestMethod.POST)
    public @ResponseBody org.orcid.pojo.ajaxForm.Email setPrimary(HttpServletRequest request, @RequestBody org.orcid.pojo.ajaxForm.Email email) {
        String orcid = getCurrentUserOrcid();
        String owner = emailManager.findOrcidIdByEmail(email.getValue());
        if(orcid.equals(owner)) {            
            // Sets the given user as primary
            emailManager.setPrimary(orcid, email.getValue().trim(), request);               
        }
        return email;
    }
    
    @RequestMapping(value = "/email/edit.json", method = RequestMethod.GET)
    public @ResponseBody EditEmail getEmailEdit(HttpServletRequest request) {                                
        return new EditEmail();
    }
    
    @RequestMapping(value = "/email/edit", method = RequestMethod.POST)
    public @ResponseBody EditEmail editEmail(HttpServletRequest request, @RequestBody EditEmail editEmail) {
        String orcid = getCurrentUserOrcid();
        String owner = emailManager.findOrcidIdByEmail(editEmail.getOriginal());

        List<String> errors = new ArrayList<String>();
        if(!orcid.equals(owner)) {            
            errors.add(getMessage("Email.personalInfoForm.email"));
        }
        
        MapBindingResult mbr = new MapBindingResult(new HashMap<String, String>(), "Email");
        
        //Clean the edited email address so it doesn't contains any horizontal white spaces
        editEmail.setEdited(OrcidStringUtils.filterEmailAddress(editEmail.getEdited()));
                
        validateEmailAddress(editEmail.getEdited(), false, false, request, mbr);

        for (ObjectError oe : mbr.getAllErrors()) {
            if (oe.getCode() != null) {
                errors.add(getMessage(oe.getCode(), oe.getArguments()));
            } else {
                errors.add(oe.getDefaultMessage());
            }
        }
        
        if (errors.isEmpty()) {
            // clear errors
            editEmail.setErrors(new ArrayList<String>());
            String original = editEmail.getOriginal();
            String edited = editEmail.getEdited();
            emailManager.editEmail(orcid, original, edited, request);            
        } else {
            editEmail.setErrors(errors);
        }
        return editEmail;
    }
    
    @RequestMapping(value = "/countryForm.json", method = RequestMethod.GET)
    public @ResponseBody AddressesForm getProfileCountryJson(HttpServletRequest request) {
        Addresses addresses = addressManager.getAddresses(getCurrentUserOrcid());
        AddressesForm form = AddressesForm.valueOf(addresses);
        // Set country name
        if (form != null && form.getAddresses() != null) {
            Map<String, String> countries = retrieveIsoCountries();
            for (AddressForm addressForm : form.getAddresses()) {
                addressForm.setCountryName(countries.get(addressForm.getIso2Country().getValue().name()));
            }
        }

        ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid());
        org.orcid.jaxb.model.v3.release.common.Visibility defaultVis = org.orcid.jaxb.model.v3.release.common.Visibility.valueOf(profile.getActivitiesVisibilityDefault());
        Visibility v = Visibility.valueOf(defaultVis);
        
        // Set the default visibility
        if (profile.getActivitiesVisibilityDefault() != null) {
            form.setVisibility(v);
        }

        return form;
    }

    @RequestMapping(value = "/countryForm.json", method = RequestMethod.POST)
    public @ResponseBody AddressesForm setProfileCountryJson(HttpServletRequest request, @RequestBody AddressesForm addressesForm) {
        addressesForm.setErrors(new ArrayList<String>());
        Map<String, String> countries = retrieveIsoCountries();
        if (addressesForm != null) {
            if (addressesForm.getAddresses() != null) {
                for (AddressForm form : addressesForm.getAddresses()) {
                    if (form.getIso2Country() == null || form.getIso2Country().getValue() == null) {
                        form.getErrors().add(getMessage("common.invalid_country"));
                    } else {
                        form.setCountryName(countries.get(form.getIso2Country().getValue().name()));
                    }
                    
                    //Validate visibility is not null
                    validateVisibility(form);
                    
                    copyErrors(form, addressesForm);
                    copyErrors(form.getVisibility(), addressesForm);
                }
            }

            if (!addressesForm.getErrors().isEmpty()) {
                return addressesForm;
            }

            Addresses addresses = addressesForm.toAddresses();
            addressManager.updateAddresses(getCurrentUserOrcid(), addresses);
        }
        return addressesForm;
    }

    @RequestMapping(value = "/nameForm.json", method = RequestMethod.GET)
    public @ResponseBody NamesForm getNameForm() {
        String currentOrcid = getCurrentUserOrcid();
        Name name = recordNameManager.getRecordName(currentOrcid);
        return NamesForm.valueOf(name);
    }
    
    @RequestMapping(value = "/nameForm.json", method = RequestMethod.POST)
    public @ResponseBody NamesForm setNameFormJson(@RequestBody NamesForm nf) {
        nf.setErrors(new ArrayList<String>());

        // Strip any html code from names before validating them
        if (!PojoUtil.isEmpty(nf.getFamilyName())) {
            nf.getFamilyName().setValue(OrcidStringUtils.stripHtml(nf.getFamilyName().getValue()));
        }

        if (!PojoUtil.isEmpty(nf.getGivenNames())) {
            nf.getGivenNames().setValue(OrcidStringUtils.stripHtml(nf.getGivenNames().getValue()));
        }

        if (!PojoUtil.isEmpty(nf.getCreditName())) {
            nf.getCreditName().setValue(OrcidStringUtils.stripHtml(nf.getCreditName().getValue()));
        }

        //Validate visibility is not null
        validateVisibility(nf);
                
        if (nf.getGivenNames() == null)
            nf.setGivenNames(new Text());
        if (nf.getFamilyName() == null)
            nf.setFamilyName(new Text());
        if (nf.getCreditName() == null)
            nf.setCreditName(new Text());
        givenNameValidate(nf.getGivenNames());
        familyNameValidate(nf.getFamilyName());
        creditNameValidate(nf.getCreditName());
        copyErrors(nf.getGivenNames(), nf);
        copyErrors(nf.getFamilyName(), nf);
        copyErrors(nf.getCreditName(), nf);
        copyErrors(nf.getVisibility(), nf);
        if (nf.getErrors().size() > 0)
            return nf;
        Name name = nf.toName();

        String orcid = getCurrentUserOrcid();
        if (recordNameManager.exists(orcid)) {
            recordNameManager.updateRecordName(orcid, name);
        } else {
            recordNameManager.createRecordName(orcid, name);
        }

        return nf;
    }

    @RequestMapping(value = "/biographyForm.json", method = RequestMethod.GET)
    public @ResponseBody BiographyForm getBiographyForm() {
        Biography bio = biographyManager.getBiography(getCurrentUserOrcid());
        BiographyForm form = BiographyForm.valueOf(bio);
        if(form.getVisibility() == null) {
            ProfileEntity profile = profileEntityCacheManager.retrieve(getCurrentUserOrcid()); 
            org.orcid.jaxb.model.v3.release.common.Visibility defaultVis = org.orcid.jaxb.model.v3.release.common.Visibility.valueOf(profile.getActivitiesVisibilityDefault());
            Visibility v = Visibility.valueOf(defaultVis);          
            form.setVisibility(v);
        }
        return form;
    }

    @RequestMapping(value = "/biographyForm.json", method = RequestMethod.POST)
    public @ResponseBody BiographyForm setBiographyFormJson(@RequestBody BiographyForm bf) {
        bf.setErrors(new ArrayList<String>());
        if (bf.getBiography() != null) {
            validateBiography(bf.getBiography());
            //Validate visibility is not null
            validateVisibility(bf);
            
            copyErrors(bf.getBiography(), bf);
            copyErrors(bf.getVisibility(), bf);
            if (bf.getErrors().size() > 0)
                return bf;

            Biography bio = new Biography();
            if (bf.getBiography() != null) {
                bio.setContent(bf.getBiography().getValue());
            }
            if (bf.getVisibility() != null && bf.getVisibility().getVisibility() != null) {
                org.orcid.jaxb.model.v3.release.common.Visibility v = org.orcid.jaxb.model.v3.release.common.Visibility.fromValue(bf.getVisibility().getVisibility().value());
                bio.setVisibility(v);
            }

            String orcid = getCurrentUserOrcid();

            if (StringUtils.isEmpty(bio.getContent())){
                if (biographyManager.exists(orcid)) {
                    biographyManager.deleteBiography(orcid);
                } else {
                    //do nothing - don't add empty bios
                }                  
            }else{
                if (biographyManager.exists(orcid)) {
                    biographyManager.updateBiography(orcid, bio);
                } else {
                    biographyManager.createBiography(orcid, bio);
                }                
            }

        }
        return bf;
    }

    /**
     * Authorize a delegate request done by an admin
     */
    @RequestMapping(value = { "/authorize-delegates" }, method = RequestMethod.GET)
    public ModelAndView authorizeDelegatesRequest(@RequestParam("key") String key) {
        try {
            Map<String, String> params = decryptDelegationKey(key);
            if (params.containsKey(AdminManager.MANAGED_USER_PARAM) && params.containsKey(AdminManager.TRUSTED_USER_PARAM)) {
                String managedOrcid = params.get(AdminManager.MANAGED_USER_PARAM);
                String trustedOrcid = params.get(AdminManager.TRUSTED_USER_PARAM);
                // Check if managed user is the same than the logged user
                if (managedOrcid.equals(getEffectiveUserOrcid())) {
                    // Check if the managed user email is verified, if not,
                    // verify it
                    verifyPrimaryEmailIfNeeded(managedOrcid);                    
                    givenPermissionToManager.create(getCurrentUserOrcid(), trustedOrcid);
                    return new ModelAndView("redirect:/manage?delegate=" + trustedOrcid);
                } else {
                    return new ModelAndView("redirect:/manage?wrongToken=true");
                }
            } else {
                return new ModelAndView("redirect:/manage?invalidToken=true");
            }
        } catch (UnsupportedEncodingException | EncryptionOperationNotPossibleException e) {
            return new ModelAndView("redirect:/manage?invalidToken=true");
        }
    }
    
    @RequestMapping(value = { "/get-trusted-orgs" }, method = RequestMethod.GET)
    public @ResponseBody List<ApplicationSummary> getTrustedOrgs() {
        return profileEntityManager.getApplications(getCurrentUserOrcid());
    }

    /**
     * @throws UnsupportedEncodingException
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> decryptDelegationKey(String encryptedKey) throws UnsupportedEncodingException {
        String jsonString = encryptionManager.decryptForExternalUse(new String(Base64.decodeBase64(encryptedKey), "UTF-8"));
        Map<String, String> params = (Map<String, String>) JsonUtils.readObjectFromJsonString(jsonString, Map.class);
        return params;
    }

    /**
     * Verify a primary email if it is not verified yet.
     * 
     * @param orcid
     *            The profile id to check
     */
    private void verifyPrimaryEmailIfNeeded(String orcid) {
        if (!emailManager.isPrimaryEmailVerified(orcid)) {
            emailManager.verifyPrimaryEmail(orcid);
        }
    }
    
    @RequestMapping(value = "/emailFrequencyOptions.json", method = RequestMethod.GET)
    public @ResponseBody EmailFrequencyOptions getEmailFrequencyOptions() {
        return emailManagerReadOnly.getEmailFrequencyOptions();
    }
}