/*
 * Copyright (c) 2009--2014 Red Hat, Inc.
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
package com.redhat.rhn.frontend.action.systems.sdc;

import com.redhat.rhn.domain.action.Action;
import com.redhat.rhn.domain.action.ActionFactory;
import com.redhat.rhn.domain.server.CPU;
import com.redhat.rhn.domain.server.Device;
import com.redhat.rhn.domain.server.NetworkInterface;
import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.domain.server.ServerFQDN;
import com.redhat.rhn.domain.server.ServerFactory;
import com.redhat.rhn.domain.server.ServerNetAddress4;
import com.redhat.rhn.domain.server.ServerNetAddress6;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.RhnAction;
import com.redhat.rhn.frontend.struts.RhnHelper;
import com.redhat.rhn.frontend.taglibs.list.ListTagHelper;
import com.redhat.rhn.manager.action.ActionManager;
import com.redhat.rhn.manager.system.SystemManager;
import com.redhat.rhn.taskomatic.TaskomaticApi;
import com.redhat.rhn.taskomatic.TaskomaticApiException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.DynaActionForm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * SystemHardwareAction handles the interaction of the ChannelDetails page.
 */
public class SystemHardwareAction extends RhnAction {

    /** Logger instance */
    private static Logger log = LogManager.getLogger(SystemHardwareAction.class);

    private static final TaskomaticApi TASKOMATIC_API = new TaskomaticApi();
    public static final String SID = "sid";

    /** {@inheritDoc} */
    @Override
    public ActionForward execute(ActionMapping mapping,
            ActionForm formIn,
            HttpServletRequest request,
            HttpServletResponse response) {

        DynaActionForm form = (DynaActionForm)formIn;
        RequestContext ctx = new RequestContext(request);
        User user =  ctx.getCurrentUser();
        Map<String, Object> params = makeParamMap(request);
        String fwd = RhnHelper.DEFAULT_FORWARD;

        Long sid = ctx.getRequiredParam(RequestContext.SID);
        Server server = SystemManager.lookupByIdAndUser(sid, user);

        CPU cpu = server.getCpu();
        Date now = new Date();
        request.setAttribute(SID, sid);

        if (isSubmitted(form)) {
            if (ctx.hasParam("update_networking_properties")) {
                server.setPrimaryInterfaceWithName(form.get("primaryInterface").toString());
                server.setPrimaryFQDNWithName(form.get("primaryFQDN").toString());
                createSuccessMessage(request, "message.interfaceSet", null);
            }
            else {
                Action a = ActionManager.scheduleHardwareRefreshAction(user, server, now);
                ActionFactory.save(a);
                try {
                    TASKOMATIC_API.scheduleActionExecution(a);
                    String[] messageParams = new String[2];
                    messageParams[0] = server.getName();
                    messageParams[1] = sid.toString();
                    createMessage(request, "message.refeshScheduled", messageParams);
                }
                catch (TaskomaticApiException e) {
                    log.error("Could not unschedule action:");
                    log.error(e);
                    createErrorMessage(request, "taskscheduler.down", StringUtils.EMPTY);
                }

                // No idea why I have to do this  :(
                params.put(SID, sid);

                fwd = "success";
            }
        }

        setupForm(request, cpu, server, form);

        SdcHelper.ssmCheck(request, server.getId(), user);

        return getStrutsDelegate().forwardParams(
                mapping.findForward(fwd), params);
    }

    private void setupForm(HttpServletRequest request, CPU cpu, Server server,
            DynaActionForm daForm) {

        if (server.findPrimaryNetworkInterface() != null) {
            daForm.set("primaryInterface", server.findPrimaryNetworkInterface().getName());
            request.setAttribute("primaryInterface",
                    server.findPrimaryNetworkInterface().getName());
        }
        if (server.getActiveNetworkInterfaces() != null) {
            request.setAttribute("networkInterfaces", getNetworkInterfaces(server));
        }
        ServerFQDN fqdn = server.findPrimaryFqdn();
        if (fqdn != null) {
            daForm.set("primaryFQDN", fqdn.getName());
            request.setAttribute("primaryFQDN", fqdn.getName());
        }
        request.setAttribute("system", server);
        if (cpu != null) {
            request.setAttribute("cpu_model", cpu.getModel());
            request.setAttribute("cpu_count", cpu.getNrCPU());
            request.setAttribute("cpu_mhz", cpu.getMHz());
            request.setAttribute("cpu_vendor", cpu.getVendor());
            request.setAttribute("cpu_stepping", cpu.getStepping());
            request.setAttribute("cpu_family", cpu.getFamily());
            request.setAttribute("cpu_arch", server.getServerArch().getName());
            request.setAttribute("cpu_cache", cpu.getCache());
            request.setAttribute("cpu_sockets", cpu.getNrsocket());
            request.setAttribute("cpu_cores", cpu.getNrCPU());
        }


        request.setAttribute("system_ram", server.getRam());
        request.setAttribute("system_swap", server.getSwap());
        request.setAttribute("machine_id", server.getMachineId());

        StringBuilder dmiBios = new StringBuilder();
        if (server.getDmi() != null) {

            if (server.getDmi().getBios() != null) {
                if (StringUtils.isNotEmpty(server.getDmi().getBios().getVendor())) {
                    dmiBios.append(server.getDmi().getBios().getVendor() + " ");
                }
                if (StringUtils.isNotEmpty(server.getDmi().getBios().getVersion())) {
                    dmiBios.append(server.getDmi().getBios().getVersion() + " ");
                }
                if (StringUtils.isNotEmpty(server.getDmi().getBios().getRelease())) {
                    dmiBios.append(server.getDmi().getBios().getRelease());
                }
            }

            request.setAttribute("dmi_vendor", server.getDmi().getVendor());
            request.setAttribute("dmi_system", server.getDmi().getSystem());
            request.setAttribute("dmi_product", server.getDmi().getProduct());
            request.setAttribute("dmi_bios", dmiBios.toString());
            request.setAttribute("dmi_asset_tag", server.getDmi().getAsset());
            request.setAttribute("dmi_board", server.getDmi().getBoard());
        }

        request.setAttribute("network_hostname", server.getDecodedHostname());
        request.setAttribute("network_ip_addr", server.getIpAddress());
        request.setAttribute("network_ip6_addr", server.getIp6Address());
        request.setAttribute("network_cnames", server.getDecodedCnames());
        request.setAttribute("fqdns", server.getFqdns());

        List<String> nicList = new ArrayList<>();
        for (NetworkInterface n : server.getNetworkInterfaces()) {
            nicList.add(n.getName());
        }
        Collections.sort(nicList);

        List<Map<String, String>> nicList2 = new ArrayList<>();
        List<Map<String, String>> nicList3 = new ArrayList<>();
        List<Map<String, String>> nicList4 = new ArrayList<>();
        for (String nicName : nicList) {
            NetworkInterface n = server.getNetworkInterface(nicName);
            boolean hasIPv4 = false;
            boolean hasIPv6 = false;
            for (ServerNetAddress4 na4 : n.getIPv4Addresses()) {
                Map<String, String> nic = new HashMap<>();
                nic.put("name", n.getName());
                nic.put("ip", na4.getAddress());
                nic.put("netmask", na4.getNetmask());
                nic.put("broadcast", na4.getBroadcast());
                nic.put("hwaddr", n.getHwaddr());
                nic.put("module", n.getModule());
                nicList2.add(nic);
                hasIPv4 = true;
            }
            for (ServerNetAddress6 na6 : n.getIPv6Addresses()) {
                Map<String, String> nic = new HashMap<>();
                nic.put("name", n.getName());
                nic.put("hwaddr", n.getHwaddr());
                nic.put("module", n.getModule());
                nic.put("ip6", na6.getAddress());
                nic.put("netmask", na6.getNetmask());
                nic.put("scope", na6.getScope());
                nicList3.add(nic);
                hasIPv6 = true;
            }
            if (!(hasIPv4 || hasIPv6)) {
                Map<String, String> nic = new HashMap<>();
                nic.put("name", n.getName());
                nic.put("hwaddr", n.getHwaddr());
                nic.put("module", n.getModule());
                nicList4.add(nic);
            }
        }
        request.setAttribute("network_interfaces", nicList2);
        request.setAttribute("ipv6_network_interfaces", nicList3);
        request.setAttribute("noip_network_interfaces", nicList4);

        List<Map<String, String>> miscDevices = new ArrayList<>();
        List<Map<String, String>> videoDevices = new ArrayList<>();
        List<Map<String, String>> audioDevices = new ArrayList<>();
        List<Map<String, String>> captureDevices = new ArrayList<>();
        List<Map<String, String>> usbDevices = new ArrayList<>();

        for (Device d : server.getDevices()) {
            Map<String, String> device = new HashMap<>();
            String desc = null;
            String vendor = null;

            if (d.getDescription() != null) {
                StringTokenizer st = new StringTokenizer(d.getDescription(), "|");
                vendor = st.nextToken();
                if (st.hasMoreTokens()) {
                    desc = st.nextToken();
                }
            }

            if (desc != null) {
                device.put("description", desc);
                device.put("vendor", vendor);
            }
            else {
                device.put("description", d.getDescription());
            }
            device.put("bus", d.getBus());
            device.put("detached", d.getDetached().toString());
            device.put("device", d.getDevice());
            device.put("driver", d.getDriver());
            device.put("pcitype", d.getPcitype().toString());
            switch (d.getDeviceClass()) {
                case "HD":
                    continue;
                case "VIDEO":
                    videoDevices.add(device);
                    break;
                case "USB":
                    usbDevices.add(device);
                    break;
                case "AUDIO":
                    audioDevices.add(device);
                    break;
                case "CAPTURE":
                    captureDevices.add(device);
                    break;
                default:
                    if (!d.getBus().equals("MISC")) {
                        miscDevices.add(device);
                    }
                    break;
            }
        }

        List<Map<String, String>> storageDevices = new ArrayList<>();
        for (Device hd : ServerFactory.lookupStorageDevicesByServer(server)) {
            Map<String, String> device = new HashMap<>();
            device.put("description", hd.getDescription());
            device.put("device", hd.getDevice());
            device.put("bus", hd.getBus());
            storageDevices.add(device);
        }

        request.setAttribute("storageDevices", storageDevices);
        request.setAttribute("videoDevices", videoDevices);
        request.setAttribute("audioDevices", audioDevices);
        request.setAttribute("miscDevices", miscDevices);
        request.setAttribute("usbDevices", usbDevices);
        request.setAttribute("captureDevices", captureDevices);

        request.setAttribute(ListTagHelper.PARENT_URL, request.getRequestURI());
    }

    private List<Map<String, String>> getNetworkInterfaces(Server s) {
        List<Map<String, String>> pages = new ArrayList<>();
        for (NetworkInterface ni : s.getActiveNetworkInterfaces()) {
            String istr = ni.getName();
            pages.add(createDisplayMap(istr, istr));
        }
        return pages;
    }

    private Map<String, String> createDisplayMap(String display, String value) {
        Map<String, String> selection = new HashMap<>();
        selection.put("display", display);
        selection.put("value", value);
        return selection;
    }

}
