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
package com.redhat.rhn.common.client;

import com.redhat.rhn.frontend.html.XmlTag;

import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientCertificate class representing the systemid file
 * /etc/sysconfig/rhn/systemid.
 */
public class ClientCertificate {

    public static final String SYSTEM_ID = "system_id";
    public static final String FIELDS = "fields";
    private final List<Member> members;
    private final Map<String, String[]> byName;
    private final Map<String, String> checksumFields;


    /**
     * Default Constructor
     */
    public ClientCertificate() {

        members = new ArrayList<>();
        byName = new HashMap<>();
        // boy this is some ugly stuff
        checksumFields = new HashMap<>();
        checksumFields.put("username", "");
        checksumFields.put("os_release", "");
        checksumFields.put("operating_system", "");
        checksumFields.put("architecture", "");
        checksumFields.put("system_id", "");
        checksumFields.put("type", "");
    }

    /**
     * Add a member to the certificate by name and values.
     * @param name Member name
     * @param values String array of values for the member.
     */
    public void addMember(String name, String[] values) {
        Member m = new Member();
        m.setName(name);
        for (String value : values) {
            m.addValue(value);
        }
        addMember(m);
    }

    /**
     * Add a member to the certificate by name and value.
     * @param name Member name
     * @param value Member value
     */
    public void addMember(String name, String value) {
        String[] values = new String[1];
        values[0] = value;
        addMember(name, values);
    }

    /**
     * Add a member to the certificate.
     * @param member Member to be added.
     */
    public void addMember(Member member) {
        members.add(member);
        byName.put(member.getName(), member.getValues());
    }

    /**
     * Returns the first value for the given field named <code>name</code>.
     * @param name field name
     * @return the first value for the given field name
     */
    public String getValueByName(String name) {
        String[] strs = getValuesByName(name);
        if (strs != null && strs.length > 0) {
            return strs[0];
        }

        return null;
    }

    /**
     * Returns all the values for the given field named <code>name</code>.
     * @param name field name
     * @return all the values for the given field name
     */
    public String[] getValuesByName(String name) {
        return byName.get(name);
    }

    /**
     * Validates the client certificate given the unique server key.
     * @param secret unique server secret key.
     * @throws InvalidCertificateException thrown if there is a problem
     * validating certificate.
     */
    public void validate(String secret) throws InvalidCertificateException {
        String signature = genSignature(secret);
        String checksum = getValueByName("checksum");

        if (!signature.equals(checksum)) {
            throw new InvalidCertificateException(
                    "Signature mismatch: computed sig(" + signature +
                    ") != given cert(" + checksum + ")");
        }
    }

    /**
     * Returns signature of certificate.
     * @param secret Server Secret for this certificate
     * @return Signature of this certificate
     * @throws InvalidCertificateException thrown if there is a problem
     * validating the certificate.
     */
    public String genSignature(String secret) throws InvalidCertificateException {

        String signature = null;

        String[] strs = getValuesByName(FIELDS);

        for (String str : strs) {
            if (checksumFields.get(str) == null) {
                throw new InvalidCertificateException("Invalid field " + str +
                        " provided while validating certificate");
            }
        }

        try {
            MessageDigest md = null;
            if (secret.length() == 32) {
                md = MessageDigest.getInstance("MD5");
            }
            else if (secret.length() == 64) {
                md = MessageDigest.getInstance("SHA-256");
            }

            // I'm not one to loop through things more than once
            // but this seems to be the algorithm found in Server.pm

            // add secret
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            md.update(secretBytes);

            // add the values for the fields
            for (String str : strs) {
                String value = getValueByName(str);
                byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
                md.update(valueBytes);
            }


            // add field names
            for (String str : strs) {
                byte[] fieldBytes = str.getBytes(StandardCharsets.UTF_8);
                md.update(fieldBytes);
            }

            // generate the digest
            byte[] digest = md.digest();

            // hexify this puppy
            signature = new String(Hex.encodeHex(digest));
        }
        catch (NoSuchAlgorithmException e) {
            throw new InvalidCertificateException(
                    "Problem getting MD5 message digest.", e);
        }

        return signature;
    }

    /**
     * Renders the certificate as an Xml document.
     * @return Xml document
     */
    public String asXml() {
        StringBuilder buf = new StringBuilder(XmlTag.XML_HDR);
        XmlTag params = new XmlTag("params");
        XmlTag param = new XmlTag("param");
        XmlTag value = new XmlTag("value");
        XmlTag struct = new XmlTag("struct");
        params.addBody(param);
        param.addBody(value);
        value.addBody(struct);

        for (Member m : members) {
            if (!m.getName().equals(FIELDS)) {
                String[] values = m.getValues();
                struct.addBody(createStringMember(m.getName(),
                        (values != null && values.length > 0) ? values[0] : ""));
            }
            else {
                struct.addBody(createFieldMember(m.getName(), m.getValues()));
            }
        }
        return buf.append(params.render()).toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return asXml();
    }

    /**
     * Utility method for building up XML string
     * @param name name of member
     * @param value value of member
     * @return XmlTag representing a &lt;member&gt;
     */
    private XmlTag createStringMember(String name, String value) {
        XmlTag member = new XmlTag("member");
        XmlTag nTag = new XmlTag("name");
        XmlTag vTag = new XmlTag("value");
        XmlTag tTag = new XmlTag("string");
        tTag.addBody(value);
        nTag.addBody(name);
        vTag.addBody(tTag);
        member.addBody(nTag);
        member.addBody(vTag);
        return member;
    }

    private XmlTag createFieldMember(String name, String[] values) {
        XmlTag member = new XmlTag("member");
        XmlTag nTag = new XmlTag("name");
        XmlTag vTag = new XmlTag("value");
        XmlTag tTag = new XmlTag("array");
        XmlTag dTag = new XmlTag("data");
        member.addBody(nTag);
        member.addBody(vTag);
        nTag.addBody(name);
        vTag.addBody(tTag);
        tTag.addBody(dTag);

        for (String value : values) {
            XmlTag v2 = new XmlTag("value");
            XmlTag sTag = new XmlTag("string");

            sTag.addBody(value);
            v2.addBody(sTag);
            dTag.addBody(v2);
        }

        return member;
    }

}
