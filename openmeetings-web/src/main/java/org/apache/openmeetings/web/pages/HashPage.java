/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.pages;

import static org.apache.openmeetings.web.app.WebSession.getRecordingId;
import static org.apache.openmeetings.web.util.OmUrlFragment.CHILD_ID;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.openmeetings.db.dao.record.RecordingDao;
import org.apache.openmeetings.db.dao.room.RoomDao;
import org.apache.openmeetings.db.entity.basic.IWsClient;
import org.apache.openmeetings.db.entity.basic.WsClient;
import org.apache.openmeetings.db.entity.record.Recording;
import org.apache.openmeetings.db.entity.room.Invitation;
import org.apache.openmeetings.db.entity.room.Invitation.Valid;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.util.FormatHelper;
import org.apache.openmeetings.db.util.LocaleHelper;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.openmeetings.web.common.IUpdatable;
import org.apache.openmeetings.web.common.MainPanel;
import org.apache.openmeetings.web.common.OmAjaxClientInfoBehavior;
import org.apache.openmeetings.web.common.OmModalCloseButton;
import org.apache.openmeetings.web.common.OmWebSocketPanel;
import org.apache.openmeetings.web.room.IconTextModal;
import org.apache.openmeetings.web.room.NetTestPanel;
import org.apache.openmeetings.web.room.RoomPanel;
import org.apache.openmeetings.web.room.VideoSettings;
import org.apache.openmeetings.web.user.record.VideoInfo;
import org.apache.openmeetings.web.user.record.VideoPlayer;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.StringValue;

public class HashPage extends BaseInitedPage implements IUpdatable {
	private static final long serialVersionUID = 1L;
	public static final String APP = "app";
	public static final String APP_TYPE_NETWORK = "network";
	public static final String APP_TYPE_SETTINGS = "settings";
	public static final String SWF = "swf";
	public static final String PANEL_MAIN = "panel-main";
	public static final String PANEL_RECORDING = "panel-recording";
	public static final String INVITATION_HASH = "invitation";
	static final String HASH = "secure";
	static final String LANG = "language";
	private final WebMarkupContainer recContainer = new WebMarkupContainer(PANEL_RECORDING);
	private final VideoInfo vi = new VideoInfo("info");
	private final VideoPlayer vp = new VideoPlayer("player");
	private boolean error = true;
	private MainPanel mp = null;
	private RoomPanel rp = null;
	private final PageParameters p;

	@SpringBean
	private RoomDao roomDao;
	@SpringBean
	private RecordingDao recDao;

	public HashPage(PageParameters p) {
		this.p = p;
	}

	private void createRoom(Long roomId) {
		getLoader().setVisible(true);
		getHeader().setVisible(false);
		// need to re-fetch Room object to initialize all collections
		Room room = roomDao.get(roomId);
		if (room != null && !room.isDeleted()) {
			error = false;
			rp = new RoomPanel(CHILD_ID, room);
			mp = new MainPanel(PANEL_MAIN, rp);
			replace(mp);
		}
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		StringValue secure = p.get(HASH);
		StringValue invitation = p.get(INVITATION_HASH);

		WebSession ws = WebSession.get();
		ws.checkHashes(secure, invitation);
		long lang = p.get(LANG).toLong(-1L);
		if (lang > -1) {
			ws.setLanguage(lang);
			ws.setLocale(LocaleHelper.getLocale(lang));
		}

		String errorMsg = getString("invalid.hash");
		recContainer.setVisible(false);
		add(new EmptyPanel(PANEL_MAIN).setVisible(false));
		if (!invitation.isEmpty()) {
			Invitation i = ws.getInvitation();
			if (i == null) {
				errorMsg = getString("error.hash.invalid");
			} else if (!i.isAllowEntry()) {
				FastDateFormat sdf = FormatHelper.getDateTimeFormat(i.getInvitee());
				errorMsg = Valid.ONE_TIME == i.getValid()
						? getString("error.hash.used")
						: String.format("%s %s - %s, %s", getString("error.hash.period")
								, sdf.format(i.getValidFrom()), sdf.format(i.getValidTo())
								, i.getInvitee().getTimeZoneId());
			} else {
				Recording rec = i.getRecording();
				if (rec != null) {
					vi.setVisible(!i.isPasswordProtected());
					vp.setVisible(!i.isPasswordProtected());
					if (!i.isPasswordProtected()) {
						vi.update(null, rec);
						vp.update(null, rec);
					}
					recContainer.setVisible(true);
					error = false;
				}
				Room r = i.getRoom();
				if (r != null && !r.isDeleted()) {
					createRoom(r.getId());
					if (i.isPasswordProtected() && rp != null) {
						mp.getChat().setVisible(false);
						rp.setOutputMarkupPlaceholderTag(true).setVisible(false);
					}
				}
			}
		} else if (!secure.isEmpty()) {
			Long recId = getRecordingId(), roomId = ws.getRoomId();
			if (recId == null && roomId == null) {
				errorMsg = getString("1599");
			} else if (recId != null) {
				recContainer.setVisible(true);
				Recording rec = recDao.get(recId);
				vi.update(null, rec);
				vp.update(null, rec);
				error = false;
			} else {
				createRoom(roomId);
			}
		}

		StringValue swf = p.get(SWF);
		StringValue app = swf.isEmpty() ? p.get(APP) : swf;
		if (!app.isEmpty()) {
			if (APP_TYPE_NETWORK.equals(app.toString())) {
				replace(new NetTestPanel(PANEL_MAIN).add(AttributeModifier.append("class", "app")));
				error = false;
			}
			if (APP_TYPE_SETTINGS.equals(app.toString())) {
				replace(new VideoSettings(PANEL_MAIN)
					.replace(new OmWebSocketPanel("ws-panel") {
						private static final long serialVersionUID = 1L;
						private WsClient c = null;

						@Override
						protected void onConnect(ConnectedMessage message) {
							c = new WsClient(message.getSessionId(), message.getKey().hashCode());
						}

						@Override
						protected IWsClient getWsClient() {
							return c;
						}
					})
					.add(new OmAjaxClientInfoBehavior() {
						private static final long serialVersionUID = 1L;

						@Override
						protected void onClientInfo(AjaxRequestTarget target, WebClientInfo info) {
							super.onClientInfo(target, info);
							target.appendJavaScript(
									String.format("VideoSettings.init(%s);VideoSettings.open();"
											, VideoSettings.getInitJson("noclient")
												.put("infoMsg", getString("close.settings.tab"))));
						}
					}));
				error = false;
			}
		}
		add(recContainer.add(vi.setShowShare(false).setOutputMarkupPlaceholderTag(true),
				vp.setOutputMarkupPlaceholderTag(true)), new InvitationPasswordDialog("i-pass", this));
		remove(urlParametersReceivingBehavior);
		add(new IconTextModal("access-denied")
				.withLabel(errorMsg)
				.withErrorIcon()
				.addButton(OmModalCloseButton.of("54"))
				.header(new ResourceModel("invalid.hash"))
				.show(error)
				);
	}

	@Override
	protected void onParameterArrival(IRequestParameters requestParameters, AjaxRequestTarget target) {
		//no-op
	}

	@Override
	public void update(AjaxRequestTarget target) {
		Invitation i = WebSession.get().getInvitation();
		if (i.getRoom() != null && rp != null) {
			rp.show(target);
		} else if (i.getRecording() != null) {
			target.add(vi.update(target, i.getRecording()).setVisible(true)
					, vp.update(target, i.getRecording()).setVisible(true));
		}
	}
}
