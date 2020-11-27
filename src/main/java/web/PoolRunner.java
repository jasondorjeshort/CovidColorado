package web;

import org.nanohttpd.protocols.http.ClientHandler;
import org.nanohttpd.protocols.http.threading.IAsyncRunner;

import library.MyExecutor;

/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 * 
 * @author jdorje@gmail.com
 */
class PoolRunner implements IAsyncRunner {
	@Override
	public void closeAll() {
		// we don't even track this.
	}

	@Override
	public void closed(ClientHandler clientHandler) {
		// we don't even track this.
	}

	@Override
	public void exec(ClientHandler code) {
		// code.run();
		MyExecutor.executeWeb(code);
	}

}