/*
 * Copyright (c) 2009--2016 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.frontend.action.channel.manage;

import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.common.security.PermissionException;
import com.redhat.rhn.common.validator.ValidatorException;
import com.redhat.rhn.domain.Identifiable;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.rhnset.RhnSet;
import com.redhat.rhn.domain.rhnset.RhnSetFactory;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.ServerFactory;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.dto.PackageOverview;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.RhnAction;
import com.redhat.rhn.frontend.struts.RhnHelper;
import com.redhat.rhn.manager.channel.ChannelManager;
import com.redhat.rhn.manager.rhnpackage.PackageManager;
import com.redhat.rhn.manager.rhnset.RhnSetDecl;
import com.redhat.rhn.manager.system.SystemManager;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the page display and actual deletion of a software channel.
 *
 */
public class DeleteChannelAction extends RhnAction {
    private static final String DISABLE_DELETE = "disableDelete";

    /** {@inheritDoc} */
    @Override
    public ActionForward execute(ActionMapping actionMapping, ActionForm actionForm, HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        RequestContext context = new RequestContext(request);
        User user = context.getCurrentUser();
        long channelId = Long.parseLong(request.getParameter("cid"));
        Channel channel = ChannelManager.lookupByIdAndUser(channelId, user);

        // Stuff the channel object into the request so the page can use its values
        request.setAttribute("channel", channel);
        // The channel doesn't carry its subscribed system count, so add this separately
        int subscribedSystemsCount = SystemManager.countSystemsSubscribedToChannel(channelId, user);
        request.setAttribute("subscribedSystemsCount", subscribedSystemsCount);

        // Load the number of systems subscribed through a trust relationship to the channel
        request.setAttribute("trustedSystemsCount",
                SystemManager.countSubscribedToChannelWithoutOrg(channel.getOrg().getId(), channelId));

        if (context.isSubmitted()) {
            if ((request.getParameter("unsubscribeSystems") != null) || subscribedSystemsCount == 0) {
                DataResult<PackageOverview> dr;
                try {
                    dr = PackageManager.listCustomPackageForChannel(channelId, user.getOrg().getId(), false);
                    List<MinionServer> minions = ServerFactory.listMinionsByChannel(channel.getId());
                    ChannelManager.deleteChannel(user, channel.getLabel());
                    Optional<Long> actionId = ChannelManager.applyChannelState(user, minions);
                    if (actionId.isPresent()) {
                        createSuccessMessage(request, "message.channel.deleted.state.scheduled", actionId.toString());
                    }
                }
                catch (PermissionException e) {
                    addMessage(request, e.getMessage());
                    return actionMapping.findForward(RhnHelper.DEFAULT_FORWARD);
                }
                catch (ValidatorException ve) {
                    getStrutsDelegate().saveMessages(request, ve.getResult());
                    request.setAttribute(DISABLE_DELETE, Boolean.TRUE);
                    return actionMapping.findForward(RhnHelper.DEFAULT_FORWARD);
                }

                createSuccessMessage(request, "message.channeldeleted", channel.getName());
                if (!dr.isEmpty()) {
                    prefillRhnSetWithElements(RhnSetDecl.DELETABLE_PACKAGE_LIST.get(user), dr.iterator());
                    Map<String, Object> params = new HashMap<>();
                    params.put("selected_channel", "all_managed_packages");
                    params.put("forwarded", "true");
                    return getStrutsDelegate().forwardParams(actionMapping.findForward("delete"), params);
                }
                return actionMapping.findForward("success");
            }
            addMessage(request, "message.channel.delete.systemssubscribed");
            return actionMapping.findForward(RhnHelper.DEFAULT_FORWARD);
        }
        else if (channel.containsDistributions()) {
            createErrorMessage(request, "message.channel.cannot-be-deleted.has-distros", null);
            request.setAttribute(DISABLE_DELETE, Boolean.TRUE);
        }
        return actionMapping.findForward(RhnHelper.DEFAULT_FORWARD);
    }

    private void prefillRhnSetWithElements(RhnSet set, Iterator<? extends Identifiable> identifiables) {
        set.clear();
        while (identifiables.hasNext()) {
            Identifiable tkn = identifiables.next();
            set.addElement(tkn.getId());
        }
        RhnSetFactory.save(set);
    }
}
