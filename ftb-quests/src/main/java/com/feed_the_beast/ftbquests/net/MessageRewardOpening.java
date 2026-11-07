package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.client.RewardOpeningClient;
import com.feed_the_beast.ftbquests.util.RewardOpening;
import net.minecraftforge.fml.relauncher.*;

/** Server-completed result only. The animation has no claim/roll message back to the server. */
public final class MessageRewardOpening extends MessageToClient
{
    private RewardOpening.Result result;
    public MessageRewardOpening(){}
    public MessageRewardOpening(RewardOpening.Result result){this.result=result;}
    @Override public NetworkWrapper getWrapper(){return FTBQuestsNetHandler.GENERAL;}
    @Override public void writeData(DataOut out){result.write(out);}
    @Override public void readData(DataIn in){result=RewardOpening.Result.read(in);}
    @Override @SideOnly(Side.CLIENT) public void onMessage(){RewardOpeningClient.receive(result);}
}
