/**
 * Copyright 2014 Sakaiproject Licensed under the
 * Educational Community License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.sakaiproject.kaltura.services;

//import java.util.ArrayList;
//import java.util.List;
import java.util.Properties;
import java.util.Map;
//import java.util.TreeMap;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.apache.commons.lang.StringUtils;
import org.imsglobal.basiclti.BasicLTIUtil;
import org.imsglobal.basiclti.BasicLTIConstants;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.util.Web;
import org.sakaiproject.authz.cover.SecurityService;

public class KalturaLTIService {
    private static final Log LOG = LogFactory.getLog(KalturaLTIService.class);

    private static ResourceLoader rb = new ResourceLoader("basiclti");
    public static final boolean verbosePrint = true;

    public static final String LTI_SECRET =    "secret";
    public static final String LTI_NEWPAGE =   "newpage";
    public static final String LTI_DEBUG = "debug";
    public static final String LTI_FRAMEHEIGHT = "frameheight";
    public static final String LTI1_PATH = "/imsblis/service/";

    public String[] launchLTIRequest(String module) {
        User user = UserDirectoryService.getCurrentUser();
        
        ToolManager toolManager =  (ToolManager) ComponentManager.get("org.sakaiproject.tool.api.ToolManager");
        String siteId = toolManager.getCurrentPlacement().getContext();
        String placementId = toolManager.getCurrentPlacement().getId();


    	return launchLTIRequest(module, user, placementId, siteId);
    }
    
    public String[] launchLTIRequest(String module, String userId, String siteId) {
        User user = null;
        try {
			user = UserDirectoryService.getUser(userId);
		} catch (UserNotDefinedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        String placementId = "placementId123";
    	return launchLTIRequest(module, user, placementId, siteId);
    }
    
    public String[] launchLTIRequest(String module, User user, String placementId, String siteId){
        // Start building up the properties
        Properties ltiProps = new Properties();
        Properties toolProps = new Properties();

        // KAF required LTI launch parameters-http://knowledge.kaltura.com/understanding-kaltura-application-framework-kaf#auth
        
        setProperty(ltiProps,BasicLTIConstants.LTI_VERSION,BasicLTIConstants.LTI_VERSION_1);
        // Let tools know we are coming from Sakai
        String sakaiVersion = ServerConfigurationService.getString("version.sakai","2");
        setProperty(ltiProps,"ext_lms", "sakai-"+sakaiVersion);
        setProperty(ltiProps,BasicLTIConstants.TOOL_CONSUMER_INFO_PRODUCT_FAMILY_CODE,
                "sakai");
        setProperty(ltiProps,BasicLTIConstants.TOOL_CONSUMER_INFO_VERSION, sakaiVersion);

        String launch_url = ServerConfigurationService.getString("kaltura.launch.url");
        if(!module.isEmpty()){
            launch_url=launch_url+"/"+ module;
        }
        String key = ServerConfigurationService.getString("kaltura.launch.key");
        String secret = ServerConfigurationService.getString("kaltura.launch.secret");
        
        setProperty(toolProps, "launch_url", launch_url);
        setProperty(toolProps, LTI_SECRET, secret );
        setProperty(toolProps, "key", key );

        int debug =1;
        int newpage =1;
        int frameheight = 0;
        setProperty(toolProps, LTI_DEBUG, debug+"");
        setProperty(toolProps, LTI_FRAMEHEIGHT, frameheight+"" );
        setProperty(toolProps, LTI_NEWPAGE, newpage+"" );

        int releasename = 1;
        int releaseemail = 1;

        if ( user != null )
        {
            setProperty(ltiProps,BasicLTIConstants.USER_ID,user.getId());

            setProperty(ltiProps,BasicLTIConstants.LIS_PERSON_SOURCEDID,user.getEid());
            if ( releasename == 1 ) {
                setProperty(ltiProps,BasicLTIConstants.LIS_PERSON_NAME_GIVEN,user.getFirstName());
                setProperty(ltiProps,BasicLTIConstants.LIS_PERSON_NAME_FAMILY,user.getLastName());
                setProperty(ltiProps,BasicLTIConstants.LIS_PERSON_NAME_FULL,user.getDisplayName());
            }
            if ( releaseemail == 1 ) {
                setProperty(ltiProps,BasicLTIConstants.LIS_PERSON_CONTACT_EMAIL_PRIMARY,user.getEmail());
                // Only send the display ID if it's different to the EID.
                if (!user.getEid().equals(user.getDisplayId())) {
                    setProperty(ltiProps,BasicLTIConstants.EXT_SAKAI_PROVIDER_DISPLAYID,user.getDisplayId());
                }
            }
        }
        
        // For My media - sent workspace id , media gallery - send site id
        Site site =  null;
        try{
            site = SiteService.getSite(siteId);
        }catch(Exception e){

        }
        if(site!=null){
            setProperty(ltiProps,BasicLTIConstants.CONTEXT_ID,site.getId());
            setProperty(ltiProps,BasicLTIConstants.CONTEXT_TITLE,site.getTitle());
        }
        // Fix up the return Url
        String returnUrl =  ServerConfigurationService.getString("basiclti.consumer_return_url",null);
        if ( returnUrl == null ) {
            returnUrl = getOurServerUrl() + LTI1_PATH + "return-url";
            Session s = SessionManager.getCurrentSession();
            if (s != null) {
                String controllingPortal = (String) s.getAttribute("sakai-controlling-portal");
                if ( controllingPortal == null ) {
                    returnUrl = returnUrl + "/site";
                } else {
                    returnUrl = returnUrl + "/" + controllingPortal;
                }
            }
            returnUrl = returnUrl + "/" + site.getId();
        }

        
        setProperty(ltiProps, BasicLTIConstants.LAUNCH_PRESENTATION_RETURN_URL, returnUrl);
        setProperty(ltiProps,BasicLTIConstants.RESOURCE_LINK_ID,placementId);

        if ( rb != null ) setProperty(ltiProps,BasicLTIConstants.LAUNCH_PRESENTATION_LOCALE,rb.getLocale().toString());
        
        String theRole = "Learner";
        if ( SecurityService.isSuperUser() )
        {
            theRole = "Instructor,Administrator,urn:lti:instrole:ims/lis/Administrator,urn:lti:sysrole:ims/lis/Administrator";
        }
        else if ( SiteService.allowUpdateSite(siteId) )
        {
            theRole = "Instructor";
        }
       setProperty(ltiProps,BasicLTIConstants.ROLES,theRole);
        // Pull in all of the custom parameters
        for(Object okey : toolProps.keySet() ) {
            String skey = (String) okey;
            if ( ! skey.startsWith(BasicLTIConstants.CUSTOM_PREFIX) ) continue;
            String value = toolProps.getProperty(skey);
            if ( value == null ) continue;
            setProperty(ltiProps, skey, value);
        }

        String oauth_callback = ServerConfigurationService.getString("basiclti.oauth_callback",null);
        // Too bad there is not a better default callback url for OAuth
        // Actually since we are using signing-only, there is really not much point 
        // In OAuth 6.2.3, this is after the user is authorized

        if ( oauth_callback == null ) oauth_callback = "about:blank";
        setProperty(ltiProps, "oauth_callback", oauth_callback);
        setProperty(ltiProps, BasicLTIUtil.BASICLTI_SUBMIT, "Press to Launch External Tool");

        String org_guid = ServerConfigurationService.getString("basiclti.consumer_instance_guid",null);
        String org_desc = ServerConfigurationService.getString("basiclti.consumer_instance_description",null);
        String org_url = ServerConfigurationService.getString("basiclti.consumer_instance_url",null);
        Map<String,String> extra = new HashMap<String,String> ();
        
        ltiProps = BasicLTIUtil.signProperties(ltiProps, launch_url, "POST",
                key, secret, org_guid, org_desc, org_url, extra);

        if ( ltiProps == null ) {
            return postError("<p>" + "Error signing message."+"</p>");
        }

        dPrint("LAUNCH III="+ltiProps);

        String debugProperty = toolProps.getProperty(LTI_DEBUG);
        boolean dodebug = "on".equals(debugProperty) || "1".equals(debugProperty);
        String postData = BasicLTIUtil.postLaunchHTML(ltiProps, launch_url, dodebug, extra);

        String [] retval = { postData, launch_url };
        return retval;

    }

    public static String[] postError(String str) {
        String [] retval = { str };
        return retval;
    }

    public static String getOurServerUrl() {
        String ourUrl = ServerConfigurationService.getString("sakai.lti.serverUrl");
        if (ourUrl == null || ourUrl.equals(""))
            ourUrl = ServerConfigurationService.getString("serverUrl");
        if (ourUrl == null || ourUrl.equals(""))
            ourUrl = ServerConfigurationService.getServerUrl();
        if (ourUrl == null || ourUrl.equals(""))
            ourUrl = "http://127.0.0.1:8080";

        if ( ourUrl.endsWith("/")  && ourUrl.length() > 2 )
            ourUrl = ourUrl.substring(0,ourUrl.length()-1);

        return ourUrl;
    }

    // To make absolutely sure we never send an XSS, we clean these values
    public static void setProperty(Properties props, String key, String value)
    {
        if ( value == null ) return;
        if ( props == null ) return;
        value = Web.cleanHtml(value);
        if ( value.trim().length() < 1 ) return;
        props.setProperty(key, value);
    }

    public static void dPrint(String str)
    {
        if ( verbosePrint ) System.out.println(str);
    }

}
