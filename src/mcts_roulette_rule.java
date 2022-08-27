import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;

import aiinterface.AIInterface;
import aiinterface.CommandCenter;
import enumerate.Action;
import enumerate.State;
import simulator.Simulator;
import struct.CharacterData;
import struct.FrameData;
import struct.GameData;
import struct.Key;
import struct.MotionData;

public class mcts_roulette_rule implements AIInterface {

    private Simulator simulator;
    private Key key;
    private CommandCenter commandCenter;
    private boolean playerNumber;
    private GameData gameData;

    private int myScore;
    private int opponentScore;

    private FrameData frameData;

    private FrameData simulatorAheadFrameData;

    private LinkedList<Action> myActions;

    private LinkedList<Action> oppActions;

    private CharacterData myCharacter;

    private CharacterData oppCharacter;

    private static final int FRAME_AHEAD = 14;

    private ArrayList<MotionData> myMotion;

    private ArrayList<MotionData> oppMotion;

    private Action[] actionAir;

    private Action[] actionGround;
    private LinkedList<move> roulette_selection_short;
    private LinkedList<move> roulette_selection_med;
    private LinkedList<move> roulette_selection_long;
    private LinkedList<Action> top;
    private Action spSkill;

    private Node rootNode;
    private int myX=0,oppX=0,distanceX=0;
    private int DISclose = 110 , DISlong = 160;


    public static final boolean DEBUG_MODE = false;

    @Override
    public void close() {
    }
    @Override
    public int initialize(GameData gameData, boolean playerNumber) {
        this.playerNumber = playerNumber;
        this.gameData = gameData;

        this.key = new Key();
        this.frameData = new FrameData();
        this.commandCenter = new CommandCenter();

        this.myActions = new LinkedList<Action>();
        this.oppActions = new LinkedList<Action>();
        this.top = new LinkedList<Action>();
        this.roulette_selection_long = new LinkedList<move>();
        this.roulette_selection_short = new LinkedList<move>();
        this.roulette_selection_med = new LinkedList<move>();

        simulator = gameData.getSimulator();

        actionAir = new Action[] { Action.AIR_GUARD, Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
                Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA, Action.AIR_D_DF_FB,
                Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA, Action.AIR_D_DB_BB };
        actionGround = new Action[] { Action.STAND_D_DB_BA, Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH,
                Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP, Action.STAND_GUARD, Action.CROUCH_GUARD, Action.THROW_A,
                Action.THROW_B, Action.STAND_A, Action.STAND_B, Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA,
                Action.STAND_FB, Action.CROUCH_FA, Action.CROUCH_FB, Action.STAND_D_DF_FA, Action.STAND_D_DF_FB,
                Action.STAND_F_D_DFA, Action.STAND_F_D_DFB, Action.STAND_D_DB_BB };
        spSkill = Action.STAND_D_DF_FC;

        for (Action act:actionAir) {
            move m = new move(act);
            this.roulette_selection_long.add(m);
            this.roulette_selection_short.add(m);
            this.roulette_selection_med.add(m);
        }
        for (Action act:actionGround) {
            move m = new move(act);
            this.roulette_selection_long.add(m);
            this.roulette_selection_short.add(m);
            this.roulette_selection_med.add(m);
        }
        move m = new move(spSkill);
        this.roulette_selection_long.add(m);
        this.roulette_selection_short.add(m);
        this.roulette_selection_med.add(m);


        myMotion = gameData.getMotionData(this.playerNumber);
        oppMotion = gameData.getMotionData(!this.playerNumber);

        return 0;
    }
    @Override
    public Key input() {
        return key;
    }
    @Override
    public void processing() {

        if (canProcessing()) {
            if (commandCenter.getSkillFlag()) {
                key = commandCenter.getSkillKey();
            }

            else {
                key.empty();
                commandCenter.skillCancel();

                oppX = oppCharacter.getCenterX();
                myX = myCharacter.getCenterX();
                distanceX = oppX - myX;
                if(distanceX<0){
                    distanceX = -1*distanceX;
                }

                if(oppCharacter.getAction().equals(spSkill))
                {
                    if(distanceX <= 201)
                    {
                        commandCenter.commandCall("8");

                    }
                    else
                    {
                        commandCenter.commandCall("FOR_JUMP");

                    }
                }else {
                    mctsPrepare(distanceX);
                    rootNode = new Node(simulatorAheadFrameData, null, myActions, oppActions, gameData, playerNumber,
                            commandCenter);
                    rootNode.createNode();

                    Action bestAction = rootNode.mcts();
                    if (mcts_roulette_rule.DEBUG_MODE) {
                        rootNode.printNode(rootNode);
                    }

                    commandCenter.commandCall(bestAction.name());
                }
                    if(distanceX>DISlong){
                        for (move r:roulette_selection_long) {
                            r.increase_counter(oppCharacter.getAction());
                        }
                    }
                    else if(distanceX<DISclose){
                        for (move r:roulette_selection_short) {
                            r.increase_counter(oppCharacter.getAction());
                        }
                    }
                    else{
                        for (move r:roulette_selection_med) {
                            r.increase_counter(oppCharacter.getAction());
                        }
                    }

                }
            }
        }
    public boolean canProcessing() {
        return !frameData.getEmptyFlag() && frameData.getRemainingFramesNumber() > 0;
    }
    public void mctsPrepare(int distanceX) {
        simulatorAheadFrameData = simulator.simulate(frameData, playerNumber, null, null, FRAME_AHEAD);

        myCharacter = simulatorAheadFrameData.getCharacter(playerNumber);
        oppCharacter = simulatorAheadFrameData.getCharacter(!playerNumber);

        setMyAction();
        setOppAction(distanceX);
    }
    public void setMyAction() {
        myActions.clear();

        int energy = myCharacter.getEnergy();

        if (myCharacter.getState() == State.AIR) {
            for (int i = 0; i < actionAir.length; i++) {
                if (Math.abs(myMotion.get(Action.valueOf(actionAir[i].name()).ordinal())
                        .getAttackStartAddEnergy()) <= energy) {
                    myActions.add(actionAir[i]);
                }
            }
        } else {
            if (Math.abs(
                    myMotion.get(Action.valueOf(spSkill.name()).ordinal()).getAttackStartAddEnergy()) <= energy) {
                myActions.add(spSkill);
            }

            for (int i = 0; i < actionGround.length; i++) {
                if (Math.abs(myMotion.get(Action.valueOf(actionGround[i].name()).ordinal())
                        .getAttackStartAddEnergy()) <= energy) {
                    myActions.add(actionGround[i]);
                }
            }
        }

    }
    public void setOppAction(int distanceX) {
        oppActions.clear();
        this.top = top_moves(distanceX);

        int energy = oppCharacter.getEnergy();
        if(this.top.size()>=5){
            this.oppActions = this.top;
        }else {
            if (oppCharacter.getState() == State.AIR) {
                for (int i = 0; i < actionAir.length; i++) {
                    if (Math.abs(oppMotion.get(Action.valueOf(actionAir[i].name()).ordinal())
                            .getAttackStartAddEnergy()) <= energy) {
                        oppActions.add(actionAir[i]);
                    }
                }
            } else {
                if (Math.abs(oppMotion.get(Action.valueOf(spSkill.name()).ordinal())
                        .getAttackStartAddEnergy()) <= energy) {
                    oppActions.add(spSkill);
                }

                for (int i = 0; i < actionGround.length; i++) {
                    if (Math.abs(oppMotion.get(Action.valueOf(actionGround[i].name()).ordinal())
                            .getAttackStartAddEnergy()) <= energy) {
                        oppActions.add(actionGround[i]);
                    }
                }
            }
        }
    }
    private int calculateScore(int p1Hp, int p2Hp, boolean playerNumber) {
        int score = 0;

        if (playerNumber) {
            if (p2Hp != 0 || p1Hp != 0) {
                score = 100 * p2Hp / (p1Hp + p2Hp);
            } else {
                score = 500;
            }
        } else {
            if (p2Hp != 0 || p1Hp != 0) {
                score = 100 * p1Hp / (p1Hp + p2Hp);
            } else {
                score = 500;
            }
        }
        return score;
    }
    @Override
    public void roundEnd(int p1Hp, int p2Hp, int frames) {
        System.out.println("P1:" + p1Hp);
        System.out.println("P2:" + p2Hp);
        myScore += calculateScore(p1Hp, p2Hp, true);
        opponentScore += calculateScore(p1Hp, p2Hp,	false);
        System.out.println("myScore:" + myScore);
        System.out.println("opponentScore:" + opponentScore);
        String results = p1Hp +", "+p2Hp+"\n";
        save_data(results);
    }
    public void save_data(String data) {
        BufferedWriter bw = null;
        try {
            File file = new File("data/aiData/mcts_roulette_rule/data.txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file,true);
            bw = new BufferedWriter(fw);
            bw.write(data);
            System.out.println("File written Successfully");

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (bw != null)
                    bw.close();
            } catch (Exception ex) {
                System.out.println("Error in closing the BufferedWriter" + ex);
            }
        }
    }
    @Override
    public void getInformation(FrameData frameData, boolean arg1) {
        // TODO Auto-generated method stub
        this.frameData = frameData;
        this.commandCenter.setFrameData(this.frameData, playerNumber);

        myCharacter = frameData.getCharacter(playerNumber);
        oppCharacter = frameData.getCharacter(!playerNumber);
    }
    LinkedList<Action> top_moves(int distance){
        if(distance>DISlong){
            LinkedList<Action> the_top_moves = new LinkedList<Action>();
            roulette_selection_long.sort(new Comparator<move>() {
                @Override
                public int compare(move o1, move o2) {
                    return o2.counter - o1.counter;
                }
            });
            for (move m:roulette_selection_long) {
                if(m.counter!=0){
                    the_top_moves.add(m.action);
                }
            }
            return the_top_moves;
        }
        if(distance<DISclose){
            LinkedList<Action> the_top_moves = new LinkedList<Action>();
            roulette_selection_short.sort(new Comparator<move>() {
                @Override
                public int compare(move o1, move o2) {
                    return o2.counter - o1.counter;
                }
            });
            for (move m:roulette_selection_short) {
                if(m.counter!=0){
                    the_top_moves.add(m.action);
                }
            }
            return the_top_moves;
        }
        else{
            LinkedList<Action> the_top_moves = new LinkedList<Action>();
            roulette_selection_med.sort(new Comparator<move>() {
                @Override
                public int compare(move o1, move o2) {
                    return o2.counter - o1.counter;
                }
            });
            for (move m:roulette_selection_med) {
                if(m.counter!=0){
                    the_top_moves.add(m.action);
                }
            }
            return the_top_moves;
        }
    }
}