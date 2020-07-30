import Icon from "react-native-vector-icons/FontAwesome5";
import {Text, View, AsyncStorage, ActivityIndicator, TextInput, Button, TouchableOpacity} from "react-native";
import * as React from "react";
import axios from 'axios';
import Config from "react-native-config";
import Style from "../Style";



export default class FuelForm extends React.Component{
    constructor(props){
        super(props);
        this.changeVolumeDebutFuel = this.changeVolumeDebutFuel.bind(this)
        this.changeVolumeFinFuel = this.changeVolumeFinFuel.bind(this)
        this.changeVolumeAjoutFuel = this.changeVolumeAjoutFuel.bind(this)
        this.getVolumes = this.getVolumes.bind(this)
        this.rearangeVolumes = this.rearangeVolumes.bind(this)
        this.state = {
            volumeDebutFuel: "",
            volumeDebutStored : false,
            volumeFinFuel : "",
            volumeFinStored : false,
            volumeAjoutFuel : "",
            volumeAjoutStored : false,
            volumes : [],
            ready : false,
        }
    }

    async componentDidMount() {
        await this.getVolumes();
        await this.rearangeVolumes();
    }

    changeVolumeDebutFuel(e){
        this.setState({volumeDebutFuel : e})
        this.setState({volumeDebutStored : false})
    }

    changeVolumeFinFuel(e){
        this.setState({volumeFinFuel : e})
        this.setState({volumeFinStored : false})
    }

    changeVolumeAjoutFuel(e){
        this.setState({volumeAjoutFuel : e})
        this.setState({volumeAjoutStored : false})
    }

     async rearangeVolumes(){
        console.log(this.state.volumes.length)
        for (let i = 0; i < this.state.volumes.length; i++){
            console.log("this.state.volumes[i].volume = " + this.state.volumes[i].volume)
            if (this.state.volumes[i].type === "début"){
                this.changeVolumeDebutFuel(this.state.volumes[i].volume)
                this.setState({volumeDebutStored : true})
            }
            if(this.state.volumes[i].type === "fin") {
                this.changeVolumeFinFuel(this.state.volumes[i].volume)
                this.setState({volumeFinStored : true})
            }
            if(this.state.volumes[i].type === "ajout"){
                this.changeVolumeAjoutFuel(this.state.volumes[i].volume)
                this.setState({volumeAjoutStored : true})
            }
        }
        this.setState({ready : true})
    }

    async getVolumes(){
        const token  = await AsyncStorage.getItem('token');
        const userId  = await AsyncStorage.getItem('userId');
        await axios({
            method: 'get',
            url: Config.API_URL + 'grutiers/'+ userId +'/carburant/'+Date.now(),
            headers: {'Authorization': 'Bearer ' + token},
        })
            .then( response => {
                if(response.status !== 200){
                    console.log(response.status);
                    alert(response.status);
                    return response.status;
                }
                this.setState({volumes : response.data})
                console.log("axios get volumes "+ JSON.stringify(response.data));
                return response.status;
            })
            .catch((error) => {
                console.log(error);
            })
    }


    async addVolume(type,volume,etatStored){
        const token  = await AsyncStorage.getItem('token');
        const userId  = await AsyncStorage.getItem('userId');
        const data = {
            "type": type,
            "volume" : volume,
        };
        axios({
            method: 'put',
            url: Config.API_URL + 'grutiers/'+userId+'/carburant',
            data : data,
            headers: {'Authorization': 'Bearer ' + token},
        })
            .then( response => {
                if(response.status !== 200){
                    console.log(response.status);
                    alert(response.status);
                    return response.status;
                }
                if(type === "début"){
                    this.setState({volumeDebutStored : true})
                }
                if(type === "fin"){
                    this.setState({volumeFinStored : true})
                }
                if(type === "ajout"){
                    this.setState({volumeAjoutStored : true})
                }
                console.log(response.status);
                return response.status;
            })
            .catch((error) => {
                console.log(error);
            })
    }

    render() {
        if(this.state.ready){
            console.log("volumes : "+this.state.volumes)
            console.log("debutStored : "+ this.state.volumeDebutStored)
            console.log("debutVolume : " + this.state.volumeDebutFuel)
            return(
                <View style={Style.cardFuel}>
                    <View style={Style.textContent}>
                        <Text numberOfLines={1} style={Style.cardTitle}>{ "Formulaire pour comptabiliser l'essence :" } </Text>
                        <Text numberOfLines={1} style={Style.cardDescription}>{ "Volume d'essence au début de la journée" }</Text>
                        <View style={{flexDirection : "row"}}>
                            <TextInput style={Style.textinput} onChangeText={(volumeDebutFuel) => this.changeVolumeDebutFuel(volumeDebutFuel)}
                                       value={this.state.volumeDebutFuel.toString()} placeholder={"Volume début de journée (L)"}/>
                            <TouchableOpacity style={{paddingTop:5}} onPress={() =>this.addVolume("début",this.state.volumeDebutFuel)}>
                                { this.state.volumeDebutStored ? <Icon name={"save"} size={30} color="#32CD32"/> : <Icon name={"save"} size={30} color="#A9A9A9"/>}
                            </TouchableOpacity>
                        </View>
                        <Text numberOfLines={1} style={Style.cardDescription}>{ "Volume d'essence à la fin de la journée" }</Text>
                        <View style={{flexDirection : "row"}}>
                            <TextInput style={Style.textinput} onChangeText={(volumeFinFuel) => this.changeVolumeFinFuel(volumeFinFuel)}
                                       value={this.state.volumeFinFuel.toString()} placeholder={"Volume en fin de journée (L)"}/>
                            <TouchableOpacity style={{paddingTop:5}} onPress={() =>this.addVolume("fin",this.state.volumeFinFuel)}>
                                { this.state.volumeFinStored ? <Icon name={"save"} size={30} color="#32CD32"/> : <Icon name={"save"} size={30} color="#A9A9A9"/>}
                            </TouchableOpacity>
                        </View>
                        <Text numberOfLines={1} style={Style.cardDescription}>{ "Volume d'essence ajouté cette journée" }</Text>
                        <View style={{flexDirection : "row"}}>
                            <TextInput style={Style.textinput} onChangeText={(volumeAjoutFuel) => this.changeVolumeAjoutFuel(volumeAjoutFuel)}
                                       value={this.state.volumeAjoutFuel.toString()} placeholder={"Volume d'essence ajouté (L)"}/>
                            <TouchableOpacity style={{paddingTop:5}} onPress={() =>this.addVolume("ajout",this.state.volumeAjoutFuel)}>
                                { this.state.volumeAjoutStored ? <Icon name={"save"} size={30} color="#32CD32"/> : <Icon name={"save"} size={30} color="#A9A9A9"/>}
                            </TouchableOpacity>
                        </View>
                        <View style={{flexDirection : "row", alignContent:"flex-start"}}>
                            <Button  title={"Annuler"} color={"#d9cfcf"} onPress={this.props.toggleShow}/>
                            <View style={{flex:0.9}}/>
                            <Button title={"Fermer"} onPress={this.props.toggleShow}/>
                        </View>
                    </View>
                </View>
            );
        }else{
            return null
        }
    }
}