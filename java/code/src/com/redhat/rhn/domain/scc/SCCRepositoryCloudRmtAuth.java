/*
 * Copyright (c) 2021 SUSE LLC
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

package com.redhat.rhn.domain.scc;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * This is a SUSE repository loaded from a PAYG instance and linked to a cloud rmt server
 */
@Entity(name = "CloudRmt")
@DiscriminatorValue("cloudrmt")
public class SCCRepositoryCloudRmtAuth extends SCCRepositoryAuth {

    // Logger instance
    private static Logger log = LogManager.getLogger(SCCRepositoryCloudRmtAuth.class);

    private static final String MIRRCRED_QUERY = "credentials=mirrcred_";

    /**
     * Default Constructor
     */
    public SCCRepositoryCloudRmtAuth() { }

    /**
     * @return the URL including authentication info
     */
    @Override
    @Transient
    public String getUrl() {
        try {
            URI url = new URI(getRepo().getUrl());
            URI credUrl = new URI(getCredentials().getUrl());

            List<String> sourceParams = new ArrayList<>(Arrays.asList(
                    StringUtils.split(Optional.ofNullable(url.getQuery()).orElse(""), '&')));
            sourceParams.add(MIRRCRED_QUERY + getCredentials().getId());
            String newQuery = StringUtils.join(sourceParams, "&");

            URI newURI = new URI(credUrl.getScheme(), url.getUserInfo(), credUrl.getHost(), credUrl.getPort(),
                    credUrl.getPath() + url.getPath(), newQuery, credUrl.getFragment());
            return newURI.toString();
        }
        catch (URISyntaxException ex) {
            log.error("Unable to parse URL: {}", getUrl());
        }
        return null;
    }

    @Override
    public <T> T fold(
            Function<SCCRepositoryBasicAuth, ? extends T> basicAuth,
            Function<SCCRepositoryNoAuth, ? extends T> noAuth,
            Function<SCCRepositoryTokenAuth, ? extends T> tokenAuth,
            Function<SCCRepositoryCloudRmtAuth, ? extends T> cloudRmtAuth) {
        return cloudRmtAuth.apply(this);
    }
}
