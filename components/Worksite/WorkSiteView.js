import React from "react";
import Style from "../../Style";
import axios from 'axios'
import {Text, ActivityIndicator, View, AsyncStorage} from "react-native";
import TrucksWorkSite from "../Truck/TrucksWorkSite";
import MapAdmin from "../Map/MapAdmin";
import MapTruck from "../Map/MapTruck";
import StopButtons from "../StopButtons";
import TimeBetween from "../Truck/TimeBetween";
export default class ListWorkSite extends React.Component {
    constructor(props) {
        super(props);
        this.getUser = this.getUser.bind(this);

        this.state = {
            typeUser : "",
            chargement: null,
            dechargement: null,
        };
    }

    componentDidMount() {
        // get typeOfUser
        this.getUser().then( res => this.setState({typeUser : res }))
        //get chargement
        axios({
            method : 'get',
            url :'https://smtp-pi.herokuapp.com/lieux/'+this.props.idChargement,
            headers: {'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6ImJiYTg0YmM3LTlmNDMtNDAxZS04ZjAyLTQ3ZTAyZDc4NDQ2OCIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTU4NzQxODQ0MX0.zRTuqPl0UbiwJn7zZSxErvBYhkhPibEZ51S4Aqgd6LI'}
        })
            .then( response => {
                if(response.status != 200){
                    console.log(response.status);
                    alert(response.status);
                    return response.status;
                }
                console.log(response.status);
                this.setState({chargement : response.data});
                return response.status;
            })
            .catch((error) => {
                console.log(error);
            });
        //get dechargement
        axios({
            method : 'get',
            url :'https://smtp-pi.herokuapp.com/lieux/'+this.props.idDechargement,
            headers: {'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6ImJiYTg0YmM3LTlmNDMtNDAxZS04ZjAyLTQ3ZTAyZDc4NDQ2OCIsInJvbGUiOiJhZG1pbiIsImlhdCI6MTU4NzQxODQ0MX0.zRTuqPl0UbiwJn7zZSxErvBYhkhPibEZ51S4Aqgd6LI'}
        })
            .then( response => {
                if(response.status != 200){
                    console.log(response.status);
                    alert(response.status);
                    return response.status;
                }
                console.log(response.status);
                this.setState({dechargement : response.data});
                return response.status;
            })
            .catch((error) => {
                console.log(error);
            })
    }

    async getUser(){
        const typeUser = await AsyncStorage.getItem('typeUser');
        console.log(typeUser);
        return typeUser
    }


    render() {
        if (this.state.chargement === null || this.state.dechargement === null) {
            return (
                <View style={{flex: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 30,}}>
                    <ActivityIndicator color="red" size="large"/>
                </View>
            )
        } else {
            if (this.state.typeUser === "truck") {
                return (
                    <View>
                        <MapTruck worksite={this.props.worksite} chargement={this.state.chargement} dechargement={this.state.dechargement}/>
                  </View>
                );
            } else {
                return (
                    <View>
                        <MapAdmin worksite={this.props.worksite} chargement={this.state.chargement} dechargement={this.state.dechargement}/>
                    </View>
                )
            }
        }
    }
}
